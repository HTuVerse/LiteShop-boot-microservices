# 微服务 vs 单体：日常开发差异指南

> 面向从 liteShop-boot3 单体过来的开发习惯迁移。读完这篇，你新增功能时就知道"代码往哪写、怎么调、怎么跑"。

---

## 0. 一图看懂工程结构

```
liteShop_admin_miniSer/
├── liteShop-dependencies    # BOM：所有版本号只在这里改
├── liteShop-common          # 公共底座：Result/异常/Sa-Token/Redis/MQ 配置/工具类
├── liteShop-api             # ★服务间契约：Feign 接口 + 跨服务 DTO/枚举 + 远程鉴权
├── liteShop-gateway         # 网关(8080)：路由 + /inner/** 拦截，无业务代码
├── liteShop-user-service    # 9001 用户/角色/权限/积分/余额
├── liteShop-coupon-service  # 9002 优惠券/核销/Quartz
├── liteShop-lottery-service # 9003 抽奖
├── liteShop-restaurant-service # 9004 点餐/订单/打印/Netty WS(9090)
├── liteShop-pay-service     # 9005 微信/支付宝（无库）
├── liteShop-message-service # 9006 短信/邮件（无库）
├── liteShop-file-service    # 9007 文件上传
├── liteShop-config-service  # 9008 系统配置/轮播图
└── liteShop-ai-service      # 9009 Spring AI 对话/SSE
```

---

## 1. 写代码的区别

### 1.1 单体时代 vs 现在：调别的领域的数据

| 场景 | 单体写法 | 微服务写法 |
|------|---------|-----------|
| 查用户 | `@Resource SysUserService`；`sysUserService.getById(id)` | `@Resource UserFeignClient`；`FeignResultUtil.unwrap(userFeignClient.getUserById(id))` |
| 核销优惠券 | `userCouponService.verifyCouponOnOrder(...)` | `FeignResultUtil.check(couponFeignClient.verifyCouponOnOrder(...))` |
| 扣余额 | 自己改 `SysUser.surplusMoney` + 插流水 | `userFeignClient.changeBalance(BalanceChangeDTO)`，由 user-service 原子完成 |
| 微信下单 | `weChatPayService.createJsapiPay(param)` | `FeignResultUtil.unwrap(payFeignClient.createWeChatJsapiPay(param))` |

**铁律：绝不在 A 服务里注入 B 服务的 Service/Mapper，也绝不在 A 服务的 SQL 里 JOIN B 服务的表**（第一阶段虽然共库能跑通，但拆库时会全部爆炸）。

### 1.2 新增一个跨服务调用的标准流程（4 步）

以"restaurant 需要查询用户等级"为例：

1. **被调方（user-service）**：在 `InnerUserController` 加一个 `/inner/user/level` 接口，返回 `Result<T>`。`/inner/**` 前缀是约定——网关会拦截外部对它的访问，只允许服务间内网调用。
2. **契约（liteShop-api）**：在 `UserFeignClient` 加对应方法签名（路径、参数、返回值三者必须与 Controller 完全一致）。
3. **跨服务传输的 DTO** 放 `liteShop-api` 的 `org.hongtu.api.dto`（新建的）或复用已下沉的共享实体（`org.hongtu.DTO.SysUser` 等）。
4. **调用方（restaurant-service）**：注入 `UserFeignClient`，用 `FeignResultUtil.unwrap(...)`（要数据）或 `FeignResultUtil.check(...)`（只要成功与否）处理返回。

```java
// 有返回值：unwrap，远端失败会抛 BusinessException（携带远端错误信息）
SysUser user = FeignResultUtil.unwrap(userFeignClient.getUserById(userId));
// 无返回值：check
FeignResultUtil.check(couponFeignClient.verifyCouponOnOrder(ucId, orderId));
```

### 1.3 类往哪放？决策表

| 类的性质 | 放哪 | 例子 |
|---------|------|------|
| 只有本服务用的 Entity/DTO/VO/Mapper/Service | 本服务模块 | `RestaurantOrder`、`DishMapper` |
| 两个以上服务都要用的实体/枚举 | `liteShop-api` | `SysUser`、`Coupon`、`CouponTypeEnum` |
| 服务间调用的参数/返回 DTO | `liteShop-api` 的 `api.dto` | `BalanceChangeDTO` |
| Feign 接口 | `liteShop-api` 的 `api.client` | `UserFeignClient` |
| 与业务无关的工具/统一返回/全局异常/中间件配置 | `liteShop-common` | `Result`、`BusinessException`、`RabbitMQConfig` |

注意：`liteShop-api` 里的共享实体沿用原包名（`org.hongtu.DTO`、`org.hongtu.enums`），所以 import 语句和单体时代长得一样——但它们物理上在 api 模块里，改动会影响所有依赖方，改前先想清楚。

### 1.4 事务：最大的思维转变

单体里一个 `@Transactional` 方法跨模块改多张表，全部原子回滚。现在 **`@Transactional` 只覆盖本服务自己的数据库操作，Feign 调用不参与回滚**。写涉及远程调用的事务方法时按优先级选择：

1. **首选：把远程写操作放在本地事务的最后一步。** 远程失败抛异常 → 本地整体回滚；本地先前的操作还没提交 → 无损。例：收银台下单把 `changeBalance` 放在库存扣减之后（见 `RestaurantOrderServiceImpl.cashierDirectOrder`）。
2. **资金操作采用短事务预留与持久对账。** 例：`payByBalance` 先短事务预留订单，事务外按订单号幂等扣款，再短事务入账；响应不明时由对账任务查余额流水，确认无扣款后封闭订单号并释放预留。
3. **被调方要保证幂等**：涉及资金/状态的内部接口用业务键（如 orderNo）做幂等，补偿重试才安全。
4. 多个远程写没法排到最后 → 该上 MQ 最终一致或 Seata 了，先在 MIGRATION.md 记技术债。

### 1.5 鉴权

- 每个服务独立完成鉴权，写法与单体一致：`StpUtil.checkLogin()`、`@SaCheckRole`、`@SaCheckPermission`、`@SaIgnore` 照常用。
- 各服务 `sa-token` 配置（`jwt-secret-key`、`token-name` 等）**必须与所有服务保持一致**，token 才能互通——新建服务时直接复制这段配置。
- 角色/权限数据只在 user-service 有库。其他服务的 `@SaCheckRole` 能生效，靠的是 `liteShop-api` 自动装配的 `RemoteStpInterface`（内部 Feign 调 user-service）。前提：该服务启动类加了 `@EnableFeignClients(basePackages = "org.hongtu.api.client")`。
- Feign 调用会自动透传当前请求的 `satoken` 头（`FeignTokenRelayInterceptor`），下游能识别"当前登录人"，不用手动传。

### 1.6 新建一个微服务的清单

1. 复制一个最像的现有服务作模板（无库的抄 pay-service，有库的抄 config-service）。
2. `pom.xml`：改 artifactId；父 pom `<modules>` 加一行。
3. 启动类：`@SpringBootApplication(scanBasePackages = "org.hongtu") + @EnableDiscoveryClient`，要调别人再加 `@EnableFeignClients(basePackages = "org.hongtu.api.client")`，无库加 `exclude = DataSourceAutoConfiguration.class`。
4. `application.yml`：独立端口（延续 900x 序列）、`spring.application.name`（`liteShop-xxx-service`）、Nacos 地址、照抄 sa-token/redis/rabbitmq 段。
5. 网关 `application.yml` 加路由，把该服务的 Controller 根路径列进 predicates。

---

## 2. 本地运行的区别

### 2.1 启动依赖（比单体多一个 Nacos）

| 中间件 | 单体 | 微服务 |
|--------|------|--------|
| MySQL / Redis / RabbitMQ | 要 | 要（不变） |
| **Nacos** (localhost:8848) | 不要 | **必须先起**，否则服务注册失败 |

Nacos 单机启动：`startup.cmd -m standalone`（Windows）。

### 2.2 启动顺序与"只启一部分"

推荐顺序：`Nacos → user-service → 其他业务服务 → gateway`。

单体时代一个启动类全起；现在**只需要启动你正在开发的服务 + 它 Feign 依赖的服务**：

| 你在改 | 至少要起 |
|--------|---------|
| user-service | 只起它自己 |
| coupon / lottery / config | 它自己 + user-service |
| restaurant-service | 它自己 + user + coupon + pay |
| pay / message / file / ai | 只起它自己 |

调试单个服务时可以不起网关，直接访问服务端口（如 `localhost:9004/dish/list`）；联调走网关统一入口 `localhost:8080`。

### 2.3 请求入口的变化

- 单体：`http://localhost:8080/xxx` 直达。
- 微服务：前端仍然只访问 `http://localhost:8080/xxx`（网关），由路由规则转发。**新增 Controller 根路径时必须同步更新网关路由**，否则外部 404。
- `/inner/**` 是服务间专用，网关无路由 + 有拦截双保险，外部永远不该访问到；本地测试内部接口直接打服务端口，如 `localhost:9001/inner/user/1`。

### 2.4 IDEA 多服务运行

用 Run Configuration 的 Services/Compound 面板管理 8 个启动类；改代码只需重启对应服务（其余服务不受影响，这是比单体爽的地方——单体改一行要整个重启）。

---

## 3. 测试的区别

### 3.1 单元测试

Service 层测试与单体基本一样，区别是**跨服务依赖 mock 的是 Feign 接口**而不是别的模块的 Service：

```java
@Mock
UserFeignClient userFeignClient;

@Test
void payByBalance_balanceNotEnough() {
    SysUser user = new SysUser();
    user.setSurplusMoney(new BigDecimal("1.00"));
    when(userFeignClient.getUserById(1L)).thenReturn(Result.success(user));
    // Feign 接口是普通 interface，mock 成本比 mock 半个 Spring 上下文低得多
}
```

注意 mock 返回值要包 `Result.success(...)` / `Result.error(...)`，因为调用方会走 `FeignResultUtil` 解包逻辑，错误分支也要测（远端返回 error code 时应抛 `BusinessException`）。

### 3.2 单服务集成测试

`@SpringBootTest` 起单个服务时，不想连 Nacos/依赖服务的话：

```yaml
# src/test/resources/application.yml 覆盖
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
```

再用 `@MockBean` 替掉所有 Feign 客户端。这样测试范围 = 本服务内部逻辑 + 本服务的库。

### 3.3 联调/端到端

- 全链路验证（如"下单→核销券→扣余额→支付回调"）必须起齐相关服务，用 Postman/前端打网关。
- 排查跨服务问题的习惯变化：单体看一个控制台日志就够；现在**一次请求的日志散在多个服务的控制台**，按时间戳和 orderNo/userId 串起来看。后续可引入 traceId 透传（技术债）。

### 3.4 契约一致性（新增的测试关注点）

单体没有的坑：`XxxFeignClient` 和被调方 `InnerXxxController` 是两份代码，**路径/参数/返回类型改了一边忘了另一边，编译不报错，运行时才 404/反序列化失败**。改内部接口时的自查清单：

1. Controller 和 FeignClient 的 `@XxxMapping` 路径、`@RequestParam` 名、`@RequestBody` 类型是否一致；
2. 返回的 `Result<T>` 泛型是否一致；
3. 双方都重启后再联调。

---

## 4. 其他日常差异速查

| 事项 | 单体 | 微服务 |
|------|------|--------|
| 加依赖版本 | 父 pom 或模块 pom | 只在 `liteShop-dependencies` BOM 管版本，服务 pom 不写版本号 |
| 改公共工具类 | 影响面=全项目，重启一次 | 影响面=所有服务，**全部服务都要重启**才生效 |
| 配置文件 | 一个 application.yml | 每服务一份；改中间件地址要改多份（后续可上 Nacos 配置中心收敛） |
| 全局异常/统一返回 | common 里，自动生效 | 不变（common 被所有服务依赖） |
| MQ | 模块间共用 | 不变，队列/交换机定义仍在 common；生产者消费者可以在不同服务 |
| 定时任务 | 随主应用 | 随所属服务（coupon 的券过期 Job 在 coupon-service，订单超时在 restaurant-service）；**服务多实例部署时 Quartz 会重复执行，扩容前要加分布式锁**（技术债） |
| WebSocket | 主应用 9090 端口 | restaurant-service 的 9090，前端直连该端口（不走网关）；restaurant 不能随意多实例（会话在单机内存） |
| 打包部署 | 1 个 jar | 9 个 jar（8 服务+网关），可独立部署、独立扩容 |

---

## 5. 一句话总结写代码的心智模型

> **"我这个功能碰不碰别的领域的数据？"**
> 不碰 → 和单体完全一样写。
> 碰 → 查 `liteShop-api` 有没有现成 Feign 方法；有就 `unwrap/check` 调用；没有就按 1.2 的 4 步加一个；涉及远程写操作时按 1.4 想清楚失败了谁回滚。
