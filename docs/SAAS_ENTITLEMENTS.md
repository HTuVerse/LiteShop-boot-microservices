# SaaS 套餐与租户权益

本模块由 `liteShop-user-service` 统一维护。套餐目录属于平台全局数据；订阅、加购、特殊调整、用量事件和计数器均带 `tenant_id`，继续受现有 MyBatis-Plus 租户拦截器保护。

## 部署

1. 备份 `liteshop_user`。
2. 新环境执行 `sql/vertical-split/22_saas_entitlements.sql`；已有旧版 SaaS 表的环境执行 `sql/vertical-split/24_saas_commercial_upgrade.sql`。
3. 执行 `sql/vertical-split/25_saas_restaurant_plans.sql`，创建基础版、专业版、连锁版、`store_count` 权益和额外门店加购项，不会自动绑定租户。
4. 部署 `liteShop-common`、`liteShop-api`、`liteShop-user-service` 和 `liteS`。
5. 将 `nacos-config/liteShop-gateway.example.yml` 发布为 Nacos 的 `liteShop-gateway.yml`，确保用户服务路由包含 `/saas/**`。
6. 超级管理员退出商户作用域，在顶级菜单“套餐与权益管理”下分别进入“套餐权益”“功能权益”“租户权益”完成配置。
7. 发布套餐版本后再向租户分配。已发布版本不可修改；新版本不会自动迁移存量租户。

`23_saas_demo.sql` 已停用，不再生成把基础菜单当权益的演示记录。

### 本地启动用户服务

内部模块有变更时，先从父工程安装最新依赖，再单独启动用户服务，避免 Maven 加载本地仓库中的旧 `liteShop-api`：

```powershell
mvn -pl liteShop-user-service -am install -DskipTests
mvn -pl liteShop-user-service spring-boot:run -DskipTests
```

用户服务可以在 RabbitMQ 暂时不可用时启动，但消息消费者会定时重连并输出连接错误；完整联调前仍需启动 `localhost:5672` 的 RabbitMQ。

加购时会把权益规则写入 `saas_subscription_addon.entitlement_snapshot`。因此以后编辑加购项只影响新购买记录，不会静默修改已经售出的权益。

## 权益合并顺序

最终权益按以下顺序计算：

1. 已发布套餐版本的增值权益。
2. 当前订阅下处于有效期内的加购快照；`quantity` 会乘以单份额度。
3. 租户特殊调整，按创建时间依次执行 `ENABLE`、`DISABLE`、`ADD` 或 `SET`。

`NULL` 不表示“不限量”。不限量必须显式设置 `unlimited=1`，未配置功能默认不可用。

表结构为后续增值服务预留四种业务类型，但当前只开放 `NUMBER` 类型的 `store_count`：

- `BOOLEAN`：功能开关，使用 `enabled` 表示套餐是否包含。
- `NUMBER`：数值/配额；旧数据中的 `QUOTA` 是兼容别名。
- `STRING`：文本配置值，例如专属域名等级。
- `ENUM`：受 `allowed_values` 约束的枚举，例如 `report_level=advanced`。

套餐价格放在 `saas_plan_version`，而不是套餐主档。月价、年价和权益一起发布后不可修改，因此 2027 年调整价格或额度时必须创建新版本，老租户仍指向旧版本。

## 业务服务接入

其他微服务使用 `UserFeignClient`，租户上下文会由现有 Feign 拦截器传递：

```java
EntitlementMutationDTO request = new EntitlementMutationDTO();
request.setFeatureCode(SaasFeatureCode.STORE_COUNT);
request.setAmount(BigDecimal.ONE);
request.setIdempotencyKey("store:create:" + commandId);

Result<EntitlementDecisionDTO> result = userFeignClient.consumeEntitlement(request);
if (!Boolean.TRUE.equals(result.getData().isAllowed())) {
    throw new BusinessException(409, "门店数量已达到套餐上限");
}
try {
    createStore();
} catch (Exception error) {
    request.setIdempotencyKey("store:create:rollback:" + commandId);
    userFeignClient.releaseEntitlement(request);
    throw error;
}
```

内部接口：

- `POST /inner/saas/check`：只检查，不占用额度。
- `POST /inner/saas/consume`：幂等、原子检查并占用额度。
- `POST /inner/saas/release`：幂等释放额度，最低回到零。

租户端只提供只读接口 `GET /saas/entitlements/me`（需登录）用于展示当前额度；`check`、`consume`、`release` 仅通过上述 `/inner/saas/*` 内部接口由其它服务经 Feign 调用，不对租户前端开放。业务代码不得判断 `planCode`，只依赖稳定的 `featureCode`。

## 配额语义

- `RESOURCE`：当前资源数量，不重置。创建时 consume，删除或失败补偿时 release。
- `PERIODIC`：周期消费量，按 `DAY`、`MONTH` 或 `BILLING_CYCLE` 分桶。
- `RATE`：目录和权益可以配置，但高频限流仍应在业务服务的 Redis 令牌桶中执行；本模块提供套餐上限来源。
- `CONCURRENT`：目录和权益可以配置，但业务服务应使用带超时的租约/信号量；任务结束后释放。

`usage_source` 用于区分用量来源：

- `REALTIME`：资源数量通过 `SaasUsageProvider` 从业务库实时读取。当前只提供 `store_count` 门店数量提供器。
- `COUNTER`：月订单、API 请求等累计指标，使用 `saas_usage_counter` 与幂等事件。
- `NONE`：开关、文本和枚举不统计用量。

硬限制通过单条 MySQL `INSERT ... ON DUPLICATE KEY UPDATE` 原子判断，避免两个并发请求同时越过上限。`store_count` 的技术契约由后端固定为 `REALTIME + RESOURCE + HARD + NONE`，管理端不能把它改成软提醒来绕过限制。

实时资源执行 `consume` 时会先使用 `GREATEST` 将原子计数器提升到业务库真实存量，再原子抢占额度。创建失败必须 `release`；删除资源后也必须 `release`。业务请求需传稳定的 `idempotencyKey`，不能用每次重试都会变化的随机值。

门店模块已落地该流程：新增门店先调用 user-service 的 `/inner/saas/consume` 原子占用 `store_count`，写库失败自动补偿释放；单个和批量删除成功后调用 `/inner/saas/release`。权益服务不可用时采用 fail-closed，拒绝新增或删除，防止额度状态悄悄失真。

## 后端权限与 RBAC

基础功能继续只由菜单/RBAC 控制，不进入 SaaS feature。增值服务同时受权益和 RBAC 约束；创建门店这类修改操作必须在 Service 中显式调用 `consume`，业务失败补偿 `release`，不能只靠前端展示或 AOP 预检查。

业务拒绝码：`4601` 功能未开通、`4602` 配额不足、`4603` 订阅不可用、`4604` 权益配置错误、`4605` 权益服务暂不可用。

## 前端产品策略

登录后调用 `GET /saas/entitlements/me`。前端 `useEntitlementStore` 会在登录、切换租户时自动刷新，可用于展示门店额度和升级提示。基础导航不根据套餐隐藏；前端展示也不是安全边界，新增门店最终由后端强制校验。

## 缓存策略

MVP 暂不缓存最终用量结果，避免门店数和剩余额度出现陈旧值。访问量上升后只缓存“套餐 + 加购 + Override”的静态解析结果：

- Redis Key：`saas:entitlement:{tenantId}:{revision}`，TTL 5～10 分钟。
- 租户订阅、加购、Override 变化时递增租户 revision。
- Feature、PlanVersion 或 Add-on 目录发布时递增全局 catalog revision。
- `used/remaining` 不进入静态缓存，仍由实时 Provider 或原子计数器计算。

不建议仅靠 TTL；权益降级、停用 API 等操作必须主动失效缓存。

## 订阅生命周期

当前实现识别 `TRIALING`、`ACTIVE`、`PAST_DUE`、`GRACE`、`SUSPENDED`、`CANCELED` 和 `EXPIRED`。分配新套餐会关闭旧的有效订阅，并同步 `sys_tenant.expire_time`，兼容已有租户入口校验。

订阅额外记录 `billing_cycle=MONTHLY/YEARLY/CUSTOM` 和 `auto_renew`。`PAST_DUE` 只能在明确的 `grace_end_at` 前继续使用；`SUSPENDED/CANCELED/EXPIRED` 不产生有效权益。自动续费、支付回调、账单和发票属于第二阶段，第一版由平台管理员确认收款后变更订阅即可。

降级不会删除已有业务数据。业务端应根据 `check` 结果阻止新增，正常情况下继续允许读取和删除，直到用量重新低于上限。当前门店删除需要同步释放原子计数；权益服务故障时 MVP 会保守拒绝删除，第二阶段可通过本地 Outbox 异步释放来消除这项可用性取舍。

## MVP 与第二阶段

第一版已覆盖：增值服务目录、版本化套餐价格与权益、租户订阅、加购快照、临时 Override、最终权益查询、原子消费/释放、实时门店用量和平台管理页。当前唯一可售且已强制执行的增值服务是 `store_count`。

第二阶段再增加：支付渠道与账单、自动续费任务、优惠券/试用转正、Redis 静态权益缓存、用量对账任务、Webhook 投递、按量阶梯计费和发票。当前不要把这些支付域逻辑塞进 user-service；达到该阶段时再提取 `saas-billing-service`。
