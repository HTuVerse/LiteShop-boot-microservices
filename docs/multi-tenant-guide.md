# 多租户改造与接入说明

## 数据层级

系统采用共享数据库、共享表的行级隔离模型：

```text
平台 SUPER_ADMIN（tenant_id = NULL）
└── 租户 / 商户（sys_tenant）
    ├── 门店（store_info）
    └── 租户数据（所有业务表 tenant_id）
        └── 门店数据（store_id）
```

- `SUPER_ADMIN`：平台账号，不属于任何租户；默认可以查看全部租户，也可以“进入租户”后按该租户操作。
- `ADMIN`：商户管理员，可以管理本租户全部门店及数据。
- `STORE_ADMIN`：门店管理员，只能管理 `sys_user_store` 授权的门店。
- `STORE_STAFF`：门店员工角色已预置，后续可按具体岗位开放操作接口。
- `USER`：租户消费者。

## 数据库部署顺序

空库重构环境：

1. 依次执行 `sql/vertical-split/01_liteshop_user.sql` 到 `06_liteshop_config.sql`。
2. 本地开发/联调环境再执行 `08_seed_development.sql`。
3. 不执行 `07_multi_tenant_upgrade.sql` 和 `99_migrate_from_hongtu.sql`，它们只服务于保留旧数据的升级/迁移。

完整执行顺序、开发账号和数据库约束见 `sql/vertical-split/README.md`。

已经使用旧版垂直拆库脚本的环境：

1. 先执行 `07_multi_tenant_upgrade.sql` 顶部的重复数据检查。
2. 所有检查均无结果后，再执行后续 DDL 和回填 SQL。
3. 确认平台 `SUPER_ADMIN` 用户、角色和关联表的 `tenant_id` 均为 `NULL`。

业务表中的 `tenant_id` 由 MyBatis-Plus 自动写入和过滤；带有 `tenantId` 属性的实体在新增、更新时还会被统一强制覆盖为当前租户，不能通过请求体篡改租户边界。缺少租户上下文时，租户表 SQL 会直接失败，不再回退到租户 1。

## 初始化使用流程

1. 管理端账号密码预认证：

   `POST /user/admin/Login?account=...&password=...`

   - 平台超级管理员验证成功后直接返回正式 Token。
   - 只有一个商户的商户管理员会直接进入该商户；只有一个商户且只授权一个门店的
     门店账号会直接进入该门店。
   - 存在多个可选商户，或门店账号在所选商户内存在多个授权门店时，不创建正式会话，
     而是返回 `selectionTicket`、`expiresInSeconds` 和带角色、门店选项的 `tenants` 列表。
   - `selectionTicket` 默认 5 分钟有效且只能使用一次。

2. 新建商户：

   `POST /sys-tenant`

   创建租户后会自动创建 `ADMIN`、`STORE_ADMIN`、`STORE_STAFF`、`USER` 四个租户角色，并按全局权限编码为前三类管理角色分配默认权限。

3. 超级管理员进入租户作用域：

   `POST /sys-tenant/{tenantId}/enter`

   进入后，平台账号仍然是 `tenant_id = NULL`，但业务 SQL 只操作所选租户。调用 `POST /sys-tenant/exit` 返回平台全局模式。

4. 在租户作用域内创建商户管理员、门店和门店账号，并分配相应角色。

5. 给门店管理员授权门店：

   `POST /sys-user-store/assign?userId={userId}&defaultStoreId={storeId}`

   请求体为门店 ID 数组，例如 `[10001, 10002]`。授权变化会同步更新在线会话中的门店范围。

6. 商户管理端选择商户并完成登录：

   `POST /user/admin/select-tenant?selectionTicket=...&tenantId=...&storeId=...`

   服务端会重新检查用户、角色和商户状态，通过后才签发正式 Token，并把所选
   `tenantId` 写入 Sa-Token 会话。商户管理员不需要传 `storeId`，其范围是当前商户的
   全部门店；门店账号必须选择一个已授权门店，会话只把该 `activeStoreId` 用作业务
   SQL 数据范围。全部授权门店仅用于校验可否切换，不再直接作为查询范围。

   当前数据模型中，同一运营人员在每个被授权商户下各有一条 `sys_user` 记录；这些记录
   使用相同的用户名/手机号/邮箱和密码时，一次预认证即可汇总为商户列表。角色与门店权限
   仍分别保存在各自租户下，不会跨商户合并。

7. 登录后切换工作空间：

   - `GET /user/admin/workspaces` 返回本次身份验证允许进入的商户、角色和门店。
   - `POST /user/admin/switch-workspace?tenantId=...&storeId=...` 切换工作空间。
   - 同一商户切门店只更新服务端 `activeStoreId`；跨商户切换会为目标租户成员重新签发
     Token 并注销旧 Token。客户端必须替换 Token、清空菜单/标签/页面缓存并重新拉取权限。

8. 普通用户认证：

   - 账号密码：`POST /user/Login?account=...&password=...`
   - 手机/邮箱验证码：`POST /user/phoneOrEmailVerifyCodeLogin?account=...&verifyCode=...`
   - 微信登录：`POST /user/wxLogin?code=...`
   - 重置密码：`POST /user/resetPassword?account=...&verifyCode=...&newPassword=...`

   上述接口均不接收 `tenantCode`。小程序或用户端应在请求拦截器中自动携带
   `X-Public-Tenant-Id`，其值来自项目部署配置、当前访问域名对应的商户配置，或扫码入口
   携带的商户信息。验证码发送接口也会优先使用该请求头作为验证码缓存作用域；后端只在
   请求头确定的商户范围内查找或创建用户。

## 请求中的租户来源

- 已登录请求：只信任 Sa-Token 会话里的 `tenantId`、超级管理员标记和门店范围。
- Feign 调用：自动透传内部租户与门店范围请求头，并携带 `X-Internal-Call` 共享密钥。
- 网关外部请求：会剥除可伪造的 `X-Tenant-Id`、`X-Super-Admin`、`X-Store-Ids` 等内部头。
- 匿名公开业务接口和普通用户登录必须传 `X-Public-Tenant-Id`。该请求头由客户端自动注入，不展示为用户输入项；它只选择公开数据所属租户，不授予管理权限，入口会向用户服务校验租户存在、启用且未到期。
- 已登录租户请求同样会复核租户状态；租户停用、到期或校验服务不可用时安全拒绝，停用租户时用户服务还会主动注销该租户会话。
- 管理端预认证、验证码、内部接口和支付回调属于特殊入口；管理端预认证会跨租户汇总授权，其他入口按各自可信字段定位租户。

WebSocket 直连 Netty 端口，不经过 Spring MVC 租户拦截器，连接地址必须同时传租户与会话：

`/ws/table-session?tenantId={tenantId}&sessionNo={sessionNo}`

服务端会在该租户上下文内验证桌台会话。共享购物车使用 Redis Key
`restaurant:cart:{tenantId}:{sessionNo}`，默认保留 24 小时，不再依赖单机内存。

## 上线检查

- Nacos 公共配置保持 `TENANT_ENABLED=true`。
- 所有服务设置相同且足够随机的 `INTERNAL_CALL_SECRET`；未配置时内部 Feign 调用会安全失败。
- 业务微服务端口和 Netty 9090 端口只向内网开放；公网统一只暴露网关和必要的 WebSocket 入口。
- 每个服务实例使用不同的雪花 `worker-id/datacenter-id` 组合。
- 检查所有历史业务表 `tenant_id` 均非空，且已正确回填为所属商户。
- 门店管理员至少分配一个 `sys_user_store` 记录；无门店授权时 SQL 自动追加 `1 = 0`。
- 平台超管给用户分配门店时，服务端会先进入目标用户租户再验证门店归属；禁止关联其他租户门店。
- `sys_permission`、`user_level` 是平台共享表，只有 `SUPER_ADMIN` 可以维护；租户管理员只能把共享权限分配给本租户角色、为本租户用户调整等级。
- 订单详情、取消和支付等顾客接口除 `tenant_id` 隔离外还校验订单所有者；桌台共享加菜必须携带与订单匹配的有效会话号，管理员继续受 `store_id` 数据范围约束。
- 支付回调必须先验签，再按全局唯一订单号定位租户；后续订单、库存、打印和 Feign 调用全部在订单所属租户内执行。
- 微信和支付宝账户按租户保存在配置库中，仅总部管理员可维护；门店账号既不能读取也不能修改，门店订单始终按订单 `tenant_id` 加载所属租户账户。部署时必须固定配置 `CONFIG_CREDENTIAL_ENCRYPTION_KEY`，并设置两个支付渠道的 HTTPS 公网回调基础地址。
- 支付分两种模式：小程序/H5 在线支付（微信 JSAPI、支付宝 WAP，用户自行触发）与收银台付款码（被扫，收银员扫顾客付款码）。付款码为近实时流程：发起后若返回「用户支付中」会轮询查单，超时自动撤单，避免「本地未支付但顾客后补扣款」的资损；下单与扣款在同一本地事务内，支付失败即回滚订单与库存预占。
- 收银台付款码：支付宝走 `alipay.trade.pay`（scene=bar_code），复用已有 SDK 无需额外配置；微信付款码走 v2 接口，租户除 JSAPI 的 v3 参数外还需在支付账户里填写 32 字节「微信 APIv2 密钥」，并在微信商户平台开通「付款码支付」产品权限，否则发起时会被明确拒绝（不回退到其它商户）。
- 管理端登录页不得提交 `tenantCode`，先提交账号密码，再使用一次性 `selectionTicket` 选择商户；小程序/用户端通过统一请求拦截器自动增加 `X-Public-Tenant-Id`。
- 用两个租户各准备一组同名用户、门店和订单，执行交叉 ID 访问测试。

## 租户处理统一实现与验证（2026-09）

- `TenantContext` 不提供默认租户；`TenantIgnore` 支持嵌套及临时挂起/恢复。HTTP、MQ 完成和异常分支均清理租户、门店与过滤旁路。
- 平台账号进入商户后启用租户过滤，平台身份查询单独保留必要的旁路。已停用商户不再允许业务查询，但平台账号仍能退出。
- `TenantInterceptor` 与 Feign 使用统一内部头常量和共享凭据；门店数据权限处理器已注册到 MyBatis-Plus。
- 角色及订单实体补齐租户字段；新建商户初始化角色时显式使用新商户上下文。角色关联表也参与租户过滤。
- 配置服务按租户延迟建立缓存，刷新只替换当前租户缓存；字符串配置值正确放入响应 `data`。
- 消息服务在未配置邮件/短信通道时可启动并返回明确错误。邮件发送失败不会缓存验证码或误报成功；验证码缓存键保留租户范围。新增 `APP_WEBSITE_NAME` 可选配置，默认 `LiteShop`。

全模块编译与原 39 项测试通过，消息修复新增 4 项测试通过，累计 43 项。真实网关联调覆盖双商户同名账号登录、一次性票据防重放、跨商户读写拒绝、门店授权、伪造内部头、配置缓存隔离及商户停用，8 组场景全部通过，临时商户数据已清理。

前端仓库 `liteS` 提供 `pnpm run test:live-tenancy` 复验脚本，参数及详细记录见该仓库的 `docs/MICROSERVICE-INTEGRATION.md` 和 `docs/live-tenant-verification.json`。该脚本仅针对本机开发服务，创建并清理自己的临时数据。
