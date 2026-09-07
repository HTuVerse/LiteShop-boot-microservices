# liteShop 单体 → 微服务架构迁移方案

> 源工程：`liteShop-boot3 - 副本 (4)`（Spring Boot 3.3.11 / Java 17 / Maven 多模块单体）
> 目标工程：`liteShop_admin_miniSer`（Spring Cloud 微服务架构）
> 数据库：`hongtu`（结构见源工程根目录 `hongtu222.sql`，共 24 张表）

---

## 1. 现状分析（单体架构）

### 1.1 模块与依赖关系

```
liteShop-boot3 (父 POM)
├── liteShop-dependencies          # BOM 版本管理
├── liteShop-module-common         # 公共底座：Sa-Token、Redis、RabbitMQ、MyBatis-Plus、统一返回/异常
├── liteShop-module-message        → common          （短信/邮件验证码，9 个类）
├── liteShop-module-user           → common, message （用户/角色/权限/积分/余额，55 个类）
├── liteShop-module-file           → common          （文件上传 本地/七牛，8 个类）
├── liteShop-module-pay            → common          （微信支付/支付宝，13 个类）
├── liteShop-module-config         → common          （系统配置/轮播图，15 个类）
├── liteShop-modules-coupons       → common, user    （优惠券/核销/Quartz 任务，32 个类）
├── liteShop-module-lottery        → common, user, coupons （抽奖，31 个类）
├── liteShop-module-restaurant     → common, pay, user, coupons （点餐/订单/打印/Netty WS，126 个类）
└── liteShop-serverStart           # 唯一启动入口，单进程 8080
```

### 1.2 技术栈

| 项 | 单体现状 |
|----|---------|
| 认证鉴权 | Sa-Token 1.42.0（JWT Simple + Redis 会话），拦截器在 common 的 `SaTokenConfigure`，权限数据在 user 的 `StpInterfaceImpl` |
| ORM | MyBatis-Plus 3.5.12，MySQL `hongtu` 单库 |
| 缓存 | Redis（db1，验证码、限流、订单超时延迟队列、Sa-Token 会话） |
| 消息队列 | RabbitMQ（发券 `coupon.issue`、用户注册 `user.register.queue`） |
| 实时推送 | restaurant 模块内嵌 Netty WebSocket，独立端口 9090，path `/ws/table-session` |
| 定时任务 | Quartz：coupons 3 个 Job；restaurant `OrderTimeoutJob`（每 5 秒扫 Redis 延迟队列） |
| 第三方 | 微信支付 wechatpay-java、支付宝 SDK、阿里云短信、七牛云、飞鹅云打印 |

### 1.3 耦合热点（拆分难点）

1. **`RestaurantOrderServiceImpl`（最重）**：直接注入
   - pay 模块：`WeChatPayService`、`AlipayService`
   - user 模块：`SysUserService`（余额支付）、`SurplusRecordMapper`
   - coupons 模块：`UserCouponService`、`UserCouponMapper`、`CouponMapper`、`CouponDishItemService`
2. **`LotteryRecordServiceImpl`**：直接调 `SysUserService.addUserPoints()`（加积分）；发券已走 RabbitMQ（天然异步边界，保留）。
3. **`UserActivityChanceServiceImpl`、`UserCouponServiceImpl`、`VerifyRecordServiceImpl`**：直接注入 user 模块的 `SysUserMapper`。
4. **`SysUserServiceImpl`**：直接注入 message 模块 `MessageService`（发验证码）。
5. **本地事务跨领域**：下单+扣余额+核销券在一个 `@Transactional` 中，拆分后必须处理分布式一致性。

---

## 2. 目标架构（微服务）

### 2.1 技术选型

| 项 | 选型 | 说明 |
|----|------|------|
| 基础框架 | Spring Boot 4.0.7 / Java 21 | Boot 4 稳定版本线，统一使用 LTS JDK 21 |
| 微服务框架 | Spring Cloud 2025.1.2 | 与 Boot 4.0.x 对齐 |
| 注册/配置中心 | Nacos（Spring Cloud Alibaba 2025.1.0.0，Nacos Client 3.1.1） | 服务发现 + 动态配置 |
| AI | Spring AI 2.0.1 | OpenAI-compatible 对话与流式输出 |
| 网关 | Spring Cloud Gateway | 统一入口 8080，路由转发 + 全局鉴权 |
| 服务间调用 | OpenFeign + Spring Cloud LoadBalancer | 同步调用（替换原直连注入） |
| 异步解耦 | RabbitMQ（保留原有） | 发券、注册等事件 |
| 认证鉴权 | Sa-Token 1.42.0（JWT + Redis） | 网关统一校验，下游通过内部请求头传递身份 |
| 分布式一致性 | 第一阶段共库本地事务；后续 MQ 最终一致 + 补偿 | 见 §5 |

### 2.2 服务划分与端口规划

按"一个业务模块 = 一个服务"的原则平移，保持包名 `org.hongtu` 不变，最大限度降低代码改动：

| 服务 | 端口 | 职责（对应表） | 来源模块 |
|------|------|---------------|---------|
| `liteShop-gateway` | **8080** | 统一入口、路由、Sa-Token 全局鉴权、CORS | 新增 |
| `liteShop-user-service` | 9001 | sys_user / sys_role / sys_permission / sys_user_role / sys_role_permission / user_level / user_points_record / user_surplus_record | module-user |
| `liteShop-coupon-service` | 9002 | coupon / coupon_dish_item / user_coupon / verify_record + Quartz 券任务 | modules-coupons |
| `liteShop-lottery-service` | 9003 | lottery_activity / lottery_prize / activity_prize / lottery_record / user_activity_chance | module-lottery |
| `liteShop-restaurant-service` | 9004（WS 9090） | dish / dish_category / dish_spec_option / store_info / table_info / table_session / restaurant_order / restaurant_order_item / print_template / printer_info + Netty WS + 订单超时 Job | module-restaurant |
| `liteShop-pay-service` | 9005 | 微信/支付宝下单与回调（无独立表） | module-pay |
| `liteShop-message-service` | 9006 | 短信/邮件验证码（无表） | module-message |
| `liteShop-file-service` | 9007 | file_upload，本地/七牛存储 | module-file |
| `liteShop-config-service` | 9008 | sys_config / sys_banner | module-config |
| `liteShop-ai-service` | 9009 | Spring AI 对话、SSE 流式输出 | 新增 |

公共模块（非服务，jar 依赖）：

| 模块 | 职责 |
|------|------|
| `liteShop-dependencies` | BOM：在原有基础上补充 Spring Cloud / Spring Cloud Alibaba 版本管理 |
| `liteShop-common` | 原 module-common 平移：统一返回 Result、异常、Redis、MQ 工具、Sa-Token 配置、常量枚举 |
| `liteShop-api` | **新增**：各服务 Feign Client 接口 + 跨服务传输 DTO（服务间契约层） |

### 2.3 请求链路

```
小程序 / 管理后台
      │
      ▼
liteShop-gateway :8080          ← Sa-Token 登录校验（JWT）、CORS、路由
      │  按路径前缀路由（lb://服务名，经 Nacos）
      ├── /user/**, /sys-*/**, /user-*/**, /surplus-record/** → user-service
      ├── /coupon/**, /user-coupon/**, /verify-record/**      → coupon-service
      ├── /lottery-*/**, /activity-prize/**, /user-activity-chance/** → lottery-service
      ├── /dish/**, /store-info/**, /table-*/**, /shared-cart/**,
      │   /restaurant-*/**, /printTemplateApi/**, /orderPrintApi/**, /feieyunApi/** → restaurant-service
      ├── /pay/**                                             → pay-service
      ├── /msg/**                                             → message-service
      ├── /file-upload/**                                     → file-service
      └── /config/**                                          → config-service

服务间同步调用（OpenFeign，内部接口统一前缀 /inner，网关拒绝外部访问 /inner/**）：
  restaurant-service → user-service   （余额扣减/退还、查用户）
  restaurant-service → coupon-service （券校验、核销、退券、查券）
  restaurant-service → pay-service    （创建支付单、退款）
  lottery-service    → user-service   （加积分、扣抽奖次数相关查询）
  coupon-service     → user-service   （查用户名等）
  user-service       → message-service（发验证码）

服务间异步（RabbitMQ，保留原有）：
  lottery-service --coupon.issue--> coupon-service （中奖发券）
  user-service    --user.register--> 消费者
```

### 2.4 鉴权方案

> 实施中调整：单体大量使用 `@SaIgnore` 注解在 Controller 方法级放行（扫码点餐、支付回调、公开配置等约 30 处），网关层无法感知注解。若在网关做统一登录校验，需要人工维护一份极易漏配的白名单。因此采用**鉴权下沉**方案，行为与单体 100% 一致。

1. **鉴权保留在各业务服务内**：每个服务经 liteShop-common 携带原 `SaTokenConfigure`（SaInterceptor 登录校验 + `@SaIgnore` 注解放行 + JWT + CORS），所有服务共用同一 Redis + 同一 `jwt-secret-key`，token 天然互通，前端无感知。
2. **角色数据加载**：user-service 保留原 `StpInterfaceImpl`（查库）；其余服务通过 liteShop-api 提供的远程 `StpInterface` 自动配置（Feign 调 user-service `/inner/user/roles`），使 `@SaCheckRole` 注解在所有服务继续生效。
3. **身份透传**：liteShop-api 提供 Feign 拦截器，服务间调用自动透传 `satoken` 请求头，下游 `StpUtil` 可正常获取当前登录人。
4. **内部接口保护**：`/inner/**` 仅限服务间调用（Feign 走注册中心直连，不经过网关）；网关全局过滤器拦截一切外部对 `/inner/**` 的访问，返回 403。
5. **网关职责**：仅路由转发 + `/inner/**` 保护，无需依赖 Redis/Sa-Token，保持轻量。

---

## 3. 数据库策略（分阶段）

**第一阶段（本次迁移执行）：拆应用、不拆库。**
所有服务连接同一个 `hongtu` 库。理由：
- 原代码存在跨表 JOIN（如订单联查券表）与外键约束，直接拆库会导致大量 SQL 重写；
- 先保证微服务应用层跑通，风险最小。

**第二阶段（后续演进，本次不执行）：按服务垂直拆库。**

| 库 | 表 |
|----|-----|
| `hongtu_user` | sys_user, sys_role, sys_permission, sys_user_role, sys_role_permission, user_level, user_points_record, user_surplus_record |
| `hongtu_coupon` | coupon, coupon_dish_item, user_coupon, verify_record |
| `hongtu_lottery` | lottery_activity, lottery_prize, activity_prize, lottery_record, user_activity_chance |
| `hongtu_restaurant` | dish, dish_category, dish_spec_option, store_info, table_info, table_session, restaurant_order, restaurant_order_item, print_template, printer_info |
| `hongtu_file` | file_upload |
| `hongtu_config` | sys_config, sys_banner |

拆库前置工作：删除跨库外键（lottery_record→sys_user、user_coupon→sys_user 等）、跨库 JOIN 改为 Feign 组装、跨服务事务改为 MQ 最终一致（见 §5）。

---

## 4. 跨服务调用改造清单（本次迁移核心工作）

| # | 原直连（单体） | 改造后 | 内部接口 |
|---|--------------|--------|---------|
| 1 | restaurant → `WeChatPayService` / `AlipayService` | Feign → pay-service | `PayFeignClient`：`/inner/pay/wechat/create`、`/inner/pay/wechat/refund`、`/inner/pay/alipay/...` |
| 2 | restaurant → `SysUserService`（余额支付/退款）、`SurplusRecordMapper` | Feign → user-service | `UserFeignClient`：`/inner/user/{id}`、`/inner/user/balance/deduct`、`/inner/user/balance/refund`（余额流水由 user-service 内部落库） |
| 3 | restaurant → `UserCouponService` / `UserCouponMapper` / `CouponMapper` / `CouponDishItemService` | Feign → coupon-service | `CouponFeignClient`：`/inner/coupon/user-coupons`、`/inner/coupon/lock`、`/inner/coupon/use`、`/inner/coupon/rollback`、`/inner/coupon/dish-items` |
| 4 | lottery → `SysUserService.addUserPoints()` | Feign → user-service | `UserFeignClient`：`/inner/user/points/add` |
| 5 | lottery → `SysUserMapper`（查用户） | Feign → user-service | `UserFeignClient`：`/inner/user/{id}` |
| 6 | coupons → `SysUserMapper`（查用户名） | Feign → user-service | `UserFeignClient`：`/inner/user/{id}`、`/inner/user/batch` |
| 7 | user → `MessageService`（验证码） | Feign → message-service | `MessageFeignClient`：`/inner/msg/sms`、`/inner/msg/email` |
| 8 | lottery --MQ--> coupons（发券） | **保留 RabbitMQ 不变** | 队列 `coupon.issue` |
| 9 | restaurant 引用 coupons 的 `CouponTypeEnum` 等枚举/DTO | 下沉到 `liteShop-api` 共享 | — |

---

## 5. 分布式事务处理

拆分后原"下单+扣余额+核销券"本地事务失效，第一阶段（共库）处理方式：

1. **共库期间**：各服务本地事务照常生效；跨服务调用（Feign）失败时由调用方**编排补偿**：
   - 下单主流程在 restaurant-service：`锁定券(Feign) → 创建订单(本地) → 扣余额/发起支付(Feign)`；
   - 任一步失败，逆序调用回滚接口（`/inner/coupon/rollback`、`/inner/user/balance/refund`）。
2. **幂等**：所有 `/inner/**` 写接口以 `orderNo` / 业务流水号做幂等键（Redis setnx 或唯一索引），补偿可重试。
3. **第二阶段拆库后**：引入 MQ 事务消息 / 本地消息表实现最终一致，或接入 Seata AT。

---

## 6. 有状态组件处理

| 组件 | 处理方式 |
|------|---------|
| Netty WebSocket (9090) | 随 restaurant-service 平移。单实例部署不受影响；多实例时后续引入 Redis Pub/Sub 广播推送 |
| Quartz（coupons 3 个 Job、restaurant 订单超时 Job） | 随各自服务平移。多实例时需分布式锁（Redis）防重复执行，本次单实例先不加 |
| Redis 延迟队列（订单超时） | 随 restaurant-service 平移，逻辑不变 |
| Sa-Token 会话 | 所有服务共用同一 Redis + 同一 JWT 密钥，会话天然共享 |
| 本地文件上传目录 `uploads/` | 归属 file-service；静态资源 `/images/**` 由 file-service 提供，网关路由 |

---

## 7. 目标工程结构

```
liteShop_admin_miniSer/
├── MIGRATION.md                        # 本文档
├── pom.xml                             # 父 POM（聚合所有模块）
├── liteShop-dependencies/              # BOM（+Spring Cloud/Alibaba 版本）
├── liteShop-common/                    # 公共底座（原 module-common 平移）
├── liteShop-api/                       # Feign 接口 + 跨服务 DTO（新增）
├── liteShop-gateway/                   # 网关 :8080
├── liteShop-user-service/              # :9001
├── liteShop-coupon-service/            # :9002
├── liteShop-lottery-service/           # :9003
├── liteShop-restaurant-service/        # :9004 (WS :9090)
├── liteShop-pay-service/               # :9005
├── liteShop-message-service/           # :9006
├── liteShop-file-service/              # :9007
├── liteShop-config-service/            # :9008
└── sql/
    └── hongtu222.sql                   # 第一阶段共库脚本（原样沿用）
```

每个业务服务内部结构（以 user-service 为例）：

```
liteShop-user-service/
├── pom.xml
└── src/main/
    ├── java/org/hongtu/           # 原模块代码平移（controller/service/DAO/DTO/VO/...）
    │   ├── UserServiceApplication.java   # 新增启动类
    │   └── inner/                        # 新增：/inner 内部接口 Controller（实现 liteShop-api 契约）
    └── resources/
        ├── application.yml               # 端口、Nacos、数据源、Redis、MQ
        └── mapper/                       # 原 XML 平移
```

---

## 8. 迁移实施步骤（执行顺序）

1. **骨架**：父 POM + liteShop-dependencies（统一管理 Spring Boot 4.0.7、Spring Cloud 2025.1.2、Spring Cloud Alibaba 2025.1.0.0、Spring AI 2.0.1、Nacos、OpenFeign、Gateway、LoadBalancer）。
2. **liteShop-common 平移**：原样拷贝，调整 pom 坐标；`SaTokenConfigure` 的全局拦截逻辑上收网关，服务内保留 Sa-Token 基础配置与工具类。
3. **liteShop-api 新建**：定义 `UserFeignClient`、`CouponFeignClient`、`PayFeignClient` 及跨服务 DTO、共享枚举（`CouponTypeEnum` 等下沉至此）。（原计划的 `MessageFeignClient` 经排查为死代码依赖，无需创建）
4. **liteShop-gateway 新建**：路由规则、`/inner/**` 内部接口拦截；鉴权由各业务服务内 Sa-Token 完成（与单体行为一致，`@SaIgnore` 继续生效）。
5. **无依赖服务优先平移**（改动最小）：message → file → config → pay。每个服务 = 拷贝源码 + 启动类 + application.yml（Nacos 注册、独立端口）。
6. **user-service 平移**：移除死代码 `MessageService` 注入；新增 `/inner/user/**` 内部接口（查用户、加积分、余额原子变动、角色/权限查询）。
7. **coupon-service 平移**：`SysUserMapper` 注入改为 `UserFeignClient`；新增 `/inner/coupon/**` 内部接口；Quartz、MQ 消费者随迁。
8. **lottery-service 平移**：`SysUserService`/`SysUserMapper` 改为 `UserFeignClient`；MQ 发券保留。
9. **restaurant-service 平移**（最大工作量）：pay/user/coupons 的直连全部替换为 Feign；共享枚举改引 liteShop-api；Netty WS、打印、订单超时 Job 随迁。
10. **编译验证**：`mvn clean package -DskipTests` 全量通过；逐服务启动冒烟。

### 实施结果（已完成）

- 全部 13 个子模块（dependencies/common/api/gateway + 9 个服务）已完成迁移，`mvn clean verify` 全量通过。
- restaurant-service 的 `RestaurantOrderServiceImpl` 完成 Feign 化改造：
  - 支付：`weChatPayService`/`alipayService` → `PayFeignClient`（预下单/查单/关单/回调验签）。
  - 用户余额：原「改 `SysUser.surplusMoney` + 插 `UserSurplusRecord`」裸操作 → `UserFeignClient.changeBalance`（user-service 本地事务原子完成，orderNo 作幂等键）。
  - 优惠券：`userCouponService`/`couponMapper`/`couponDishItemService` → `CouponFeignClient`（核销/回滚/叠加校验/批量查询/免单菜品明细）。
- 分布式一致性处理：
  - `payByBalance`：远程扣款成功后本地处理失败时，自动调用退款类型补偿（幂等，失败记日志待对账）。
  - 收银台下单/桌台结算：远程扣款刻意置于本地库存扣减之后，远程失败即本地事务整体回滚，无需补偿。
  - 已知技术债：创建订单时优惠券远程核销后，若后续订单项写入失败，本地回滚但券已核销，需依赖取消订单的返还逻辑或人工对账（第二阶段可引入 MQ 最终一致或 Seata）。

---

## 9. 风险与注意事项

- **Feign 返回值**：内部接口统一返回 `Result<T>`，调用方判断 code 并抛 `BusinessException`，避免静默失败。
- **回调地址变化**：微信/支付宝/飞鹅云回调需经网关路由到对应服务，白名单放行；上线时更新第三方平台回调 URL。
- **跨服务 JOIN**：第一阶段共库，个别 Mapper 跨领域 JOIN 的 SQL 暂可运行，但属于技术债，标记 TODO 待第二阶段拆库时改造。
- **启动依赖**：需先启动 Nacos(8848)、MySQL、Redis、RabbitMQ，再启动各服务；网关最后启动。
- **组件扫描**：各服务启动类 `scanBasePackages = "org.hongtu"`，注意 common 中的自动配置对所有服务生效（如 RabbitMQ 配置），无 MQ 的服务（file/config）可按需排除。
