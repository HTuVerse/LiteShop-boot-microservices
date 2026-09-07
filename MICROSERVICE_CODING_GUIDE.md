# liteShop 微服务编码入门指南

> 适用项目：`liteShop_admin_miniSer`  
> 适用阶段：已经从单体拆成多个 Spring Boot 服务，但暂时共用一个 MySQL 数据库  
> 目标读者：熟悉 Controller、Service、Mapper、CRUD 和 `@Autowired/@Resource`，刚开始学习微服务开发

---

## 1. 先记住一句话

在单体项目中，另一个模块仍然和当前模块运行在同一个 JVM 进程里，所以可以直接注入它的 Service：

```java
@Resource
private SysUserService sysUserService;

SysUser user = sysUserService.getById(userId);
```

在微服务项目中，`restaurant-service` 和 `user-service` 是两个独立进程。它们可能运行在不同机器、不同容器里，因此不能直接注入对方的 Service 或 Mapper。

微服务中的基本规则是：

> 本服务的数据用本服务的 Service/Mapper；别的服务的数据通过接口调用。

在本项目中，服务间同步调用使用 OpenFeign，异步通知使用 RabbitMQ。

---

## 2. 当前项目的服务边界

先根据业务判断代码应该放在哪个服务，再开始写 CRUD。

| 服务 | 端口 | 负责的业务和数据 |
|---|---:|---|
| `liteShop-gateway` | 8080 | 外部统一入口、路由转发、阻止外部访问 `/inner/**` |
| `liteShop-user-service` | 9001 | 用户、角色、权限、等级、积分、余额及余额流水 |
| `liteShop-coupon-service` | 9002 | 优惠券、用户券、核销记录 |
| `liteShop-lottery-service` | 9003 | 抽奖活动、奖品、中奖记录、抽奖次数 |
| `liteShop-restaurant-service` | 9004 | 门店、餐桌、菜品、订单、打印、桌位会话、WebSocket |
| `liteShop-pay-service` | 9005 | 微信和支付宝 SDK、签名验签、支付平台请求 |
| `liteShop-message-service` | 9006 | 短信、邮件和验证码 |
| `liteShop-file-service` | 9007 | 文件上传、本地文件和七牛云存储 |
| `liteShop-config-service` | 9008 | 系统配置、轮播图等业务配置 |

非运行服务：

| 模块 | 用途 |
|---|---|
| `liteShop-common` | 与具体业务无关的公共能力，例如统一返回、异常、Redis、认证基础配置 |
| `liteShop-api` | 服务间调用契约，例如 Feign Client、请求 DTO、响应 DTO |
| `liteShop-dependencies` | 统一管理依赖版本 |

判断代码归属时，可以问自己：

1. 这个功能主要修改哪一类数据？
2. 哪个服务应该对这类数据的正确性负责？
3. 其他服务是否只需要调用它，而不应该知道它如何操作数据库？

例如“扣用户余额”必须由 `user-service` 完成。即使订单服务也连接到了同一个数据库，`restaurant-service` 也不应该直接更新 `sys_user.surplus_money`。

---

## 3. 旧 SQL 还能不能运行？

可以。

已经核对：

- 新项目的 `sql/hongtu222.sql`
- 旧单体项目根目录的 `hongtu222.sql`

两份文件内容一致，都是 30 张表。当前各业务服务的配置也都指向同一个 `hongtu` 数据库，所以现阶段可以继续使用以前的 SQL。

使用方法：

1. 创建一次 `hongtu` 数据库。
2. 只执行一次 `sql/hongtu222.sql`。
3. 各服务连接同一个数据库，不要为每个服务重复导入一遍。
4. 启动 MySQL、Redis、RabbitMQ 和 Nacos。
5. 再启动需要联调的业务服务与网关。

需要特别理解：

> 共用数据库，不等于共用事务。

`restaurant-service` 的 `@Transactional` 只管理订单服务自己的数据库连接。它通过 Feign 调用 `user-service` 后，用户服务会开启自己的数据库连接和事务。即使两个连接访问的是同一个 `hongtu` 库，也不会自动组成一个事务。

因此下面这种理解是错误的：

```text
restaurant-service 开启 @Transactional
    -> 保存订单
    -> Feign 调 user-service 扣余额
    -> restaurant-service 抛异常

错误理解：保存订单和扣余额都会一起回滚
实际结果：订单可能回滚，但余额扣款已经在 user-service 提交
```

暂时不分库是合理的学习路线，但从现在开始仍应遵守数据归属边界。这样以后真正分库时，改动会小很多。

---

## 4. 哪些 CRUD 写法和单体完全一样？

只操作本服务负责的数据时，写法基本不变。

例如在 `restaurant-service` 中新增菜品：

```java
@RestController
@RequestMapping("/dish")
public class DishController {

    @Resource
    private DishService dishService;

    @PostMapping
    public Result<Void> create(@Valid @RequestBody DishCreateRequest request) {
        dishService.createDish(request);
        return Result.success();
    }
}
```

```java
@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish>
        implements DishService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDish(DishCreateRequest request) {
        Dish dish = new Dish();
        dish.setName(request.getName());
        dish.setPrice(request.getPrice());
        save(dish);
    }
}
```

这类操作符合以下条件：

- Controller、Service、Mapper 都在同一个服务中。
- 操作的表属于当前服务。
- 没有调用其他服务的写接口。
- `@Transactional` 可以正常覆盖这些本地数据库操作。

因此，不要因为项目变成微服务，就把所有 CRUD 都改成 Feign。只有跨服务时才需要 Feign。

---

## 5. 单体和微服务写法的核心区别

### 5.1 查询本服务数据

可以继续注入本服务的 Service 或 Mapper：

```java
@Resource
private RestaurantOrderMapper restaurantOrderMapper;
```

### 5.2 查询其他服务数据

禁止这样写：

```java
// 错误：restaurant-service 不应该依赖 user-service 的实现类
@Resource
private SysUserService sysUserService;

// 错误：即使现在共库，也不应该跨服务直接操作对方的表
@Resource
private SysUserMapper sysUserMapper;
```

应该注入 Feign Client：

```java
@Resource
private UserFeignClient userFeignClient;

SysUser user = FeignResultUtil.unwrap(
        userFeignClient.getUserById(userId)
);
```

本项目已有三个主要客户端：

- `UserFeignClient`
- `CouponFeignClient`
- `PayFeignClient`

它们位于 `liteShop-api/src/main/java/org/hongtu/api/client`。

### 5.3 修改其他服务数据

也必须调用对方提供的业务接口，不能直接写对方的表。

例如订单服务扣用户余额：

```java
BigDecimal newBalance = FeignResultUtil.unwrap(
        userFeignClient.changeBalance(
                BalanceChangeDTO.builder()
                        .userId(userId)
                        .amount(payAmount)
                        .type(BalanceResourceEnum.EXPENSE.getCode())
                        .orderNo(orderNo)
                        .operateUserId(operatorId)
                        .build()
        )
);
```

这样余额校验、余额更新和余额流水都由 `user-service` 负责。

---

## 6. 在本项目中新加一个跨服务接口

假设 `restaurant-service` 需要查询用户等级。

### 第一步：先确定谁提供数据

用户等级属于 `user-service`，所以接口实现在 `user-service`。

### 第二步：在 `liteShop-api` 定义传输 DTO

不要直接把数据库 Entity 当成长期接口协议。推荐新建只包含调用方需要字段的 DTO：

```java
@Data
public class UserLevelDTO implements Serializable {
    private Long userId;
    private Integer levelCode;
    private String levelName;
}
```

### 第三步：在 Feign Client 中声明接口

```java
@FeignClient(name = "liteShop-user-service", contextId = "userFeignClient")
public interface UserFeignClient {

    @GetMapping("/inner/user/{id}/level")
    Result<UserLevelDTO> getUserLevel(@PathVariable("id") Long userId);
}
```

注意以下内容必须和服务提供方完全一致：

- HTTP 方法：`GET`、`POST` 等。
- 路径。
- `@PathVariable` 和 `@RequestParam` 的参数名。
- 请求体类型。
- 返回类型。

### 第四步：在 `user-service` 实现内部接口

```java
@RestController
@RequestMapping("/inner/user")
public class InnerUserController {

    @Resource
    private UserLevelService userLevelService;

    @GetMapping("/{id}/level")
    public Result<UserLevelDTO> getUserLevel(@PathVariable("id") Long userId) {
        return Result.success(userLevelService.getUserLevelDTO(userId));
    }
}
```

Controller 应尽量调用 Service，不要在 Controller 中堆积数据库查询和业务规则。

### 第五步：在调用方使用 Feign

```java
@Resource
private UserFeignClient userFeignClient;

public UserLevelDTO loadUserLevel(Long userId) {
    return FeignResultUtil.unwrap(userFeignClient.getUserLevel(userId));
}
```

使用 `FeignResultUtil.unwrap()` 的原因是：远端 HTTP 请求成功，并不代表业务执行成功。远端可能返回 `Result.error(...)`，调用方必须把它当成失败处理。

如果接口不需要返回数据，则使用：

```java
FeignResultUtil.check(remoteResult);
```

### 第六步：确认调用方启用了 Feign

调用其他服务的启动类需要：

```java
@EnableFeignClients(basePackages = "org.hongtu.api.client")
```

### 第七步：决定是否需要添加网关路由

- `/inner/**`：仅服务间调用，不添加给外部使用的路由。
- 前端需要访问的新 Controller 根路径：必须在 `liteShop-gateway` 的 `application.yml` 中添加对应 Path 路由。

---

## 7. Feign 调用和普通方法调用有什么不同？

单体中的方法调用通常是内存调用，速度快，失败类型也比较简单。

Feign 是网络调用，必须考虑：

- 对方服务没有启动。
- Nacos 中找不到服务实例。
- 网络超时。
- 对方服务返回业务失败。
- 请求已经在对方执行成功，但调用方没有收到响应。
- 同一个请求因为重试而执行两次。
- DTO 字段或接口路径两边不一致。

所以跨服务代码不能只写“调用一下”就结束。至少要考虑以下规则。

### 7.1 查询接口

查询通常可以安全重试，但仍要配置合理的连接超时和读取超时，避免一个服务卡住整条请求链。

### 7.2 写接口

写接口必须支持幂等。调用方重复请求时，结果应该和只执行一次相同。

建议每次业务操作都有唯一的 `requestId`，例如：

```text
BALANCE_EXPENSE_ORDER_202608280001
BALANCE_REFUND_ORDER_202608280001
COUPON_VERIFY_ORDER_202608280001_10023
```

服务提供方在数据库中为 `request_id` 建唯一索引。收到重复请求时，直接返回第一次的处理结果，不要再次扣款或再次发券。

---

## 8. `@Transactional` 在微服务中的正确理解

### 8.1 本地事务

下面的事务是可靠的，因为都发生在同一个服务、同一个数据源连接中：

```java
@Transactional(rollbackFor = Exception.class)
public void changeBalance(...) {
    // user-service 自己更新 sys_user
    // user-service 自己插入 user_surplus_record
}
```

### 8.2 包含 Feign 的事务

下面不能保证整体回滚：

```java
@Transactional(rollbackFor = Exception.class)
public void createOrder(...) {
    saveOrder();
    couponFeignClient.verifyCouponOnOrder(...);
    saveOrderItems();
}
```

可能发生：

1. 订单服务保存订单。
2. 优惠券服务成功核销券并提交自己的事务。
3. 订单服务保存订单明细失败。
4. 订单事务回滚。
5. 最终订单不存在，但优惠券已经被使用。

即使把 Feign 写操作放到方法最后，也不能保证绝对安全，因为 Spring 通常在方法返回后才真正提交本地事务。本地提交仍然可能失败。

### 8.3 现阶段推荐做法

对于重要写操作，至少做到：

1. 每个远程写接口幂等。
2. 记录业务状态，例如 `PENDING`、`SUCCESS`、`FAILED`、`COMPENSATING`。
3. 明确补偿动作，例如扣余额对应退余额，核销券对应返还券。
4. 补偿也必须幂等，并且可以重试。
5. 失败不能只写日志，必须能被定时对账或人工任务再次处理。

业务非常重要时，推荐逐步使用“本地消息表/Outbox + RabbitMQ + 消费幂等”的最终一致性方案，而不是认为一个 `@Transactional` 可以覆盖所有服务。

---

## 9. 本项目余额业务需要特别注意

余额属于资金数据，应比普通 CRUD 更严格。

### 9.1 避免先查询再直接覆盖余额

下面的方式在并发支付时可能发生丢失更新：

```java
SysUser user = mapper.selectById(userId);
BigDecimal newBalance = user.getSurplusMoney().subtract(amount);
user.setSurplusMoney(newBalance);
mapper.updateById(user);
```

推荐使用带条件的原子 SQL：

```sql
UPDATE sys_user
SET surplus_money = surplus_money - #{amount},
    update_time = NOW()
WHERE id = #{userId}
  AND surplus_money >= #{amount};
```

受影响行数为 0 时，表示用户不存在或余额不足。

### 9.2 扣款和退款不能共用同一个唯一键

当前 SQL 中 `user_surplus_record.order_no` 是单列唯一索引。如果同一订单先插入“消费”流水，再用同一个订单号插入“退款”流水，退款记录会与消费记录冲突。

更合适的方案有两个：

- 增加独立的 `request_id` 并设置唯一索引。
- 或使用 `(order_no, type)` 联合唯一索引，但要考虑部分退款、多次退款等场景。

推荐独立 `request_id`，因为表达最清晰，也更适合重试。

---

## 10. 什么时候用 Feign，什么时候用 RabbitMQ？

### 使用 Feign

调用方必须立即得到结果时使用，例如：

- 下单前查询用户。
- 检查优惠券是否可用。
- 扣余额并马上知道是否成功。
- 创建微信支付单并把支付参数返回前端。

特点：同步、容易理解，但被调用服务不可用时，当前请求通常也会失败。

### 使用 RabbitMQ

不要求当前请求立即得到最终结果时使用，例如：

- 用户注册后发送欢迎消息。
- 抽奖成功后发放优惠券。
- 记录操作日志。
- 发送通知。

特点：解耦、削峰、可以重试，但必须处理消息可靠性和重复消费。

### RabbitMQ 编码规则

1. 消息必须包含唯一 `eventId`。
2. 生产消息与本地业务状态要保证一致，重要业务推荐 Outbox。
3. 消费者先按 `eventId` 判断是否已处理。
4. 处理成功后再确认消息。
5. 处理失败不能捕获异常后直接正常返回，否则 AUTO ACK 会认为消费成功，消息会丢失。
6. 配置重试次数和死信队列，超过次数后进入人工处理或补偿流程。
7. 不要发送庞大的数据库 Entity，发送稳定、最小化的事件 DTO。

本项目当前的优惠券消费者会捕获异常后正常返回，这会让失败消息被自动确认。后续改造时应优先修正这一点。

---

## 11. DTO、Entity 和 VO 应该怎么放？

### Entity

Entity 对应本服务拥有的数据库表，只放在拥有该表的服务中。

例如：

- `SysUser` 的数据库 Entity 属于 `user-service`。
- `RestaurantOrder` 属于 `restaurant-service`。
- `UserCoupon` 属于 `coupon-service`。

### API DTO

跨服务请求和响应 DTO 放在 `liteShop-api`，只暴露调用方真正需要的字段。

例如餐厅服务查询用户时，通常不应该获得密码、openid、完整权限信息等字段。可以定义：

```java
@Data
public class UserSummaryDTO {
    private Long id;
    private String nickname;
    private BigDecimal surplusMoney;
}
```

### VO

返回给前端的 VO 放在提供外部接口的业务服务中。

长期建议：

> Entity 不跨服务，API DTO 才跨服务。

当前项目为了降低迁移成本，`liteShop-api` 中仍放了一些带 MyBatis-Plus 注解的共享 Entity。这在过渡期可以运行，但会让数据库结构变化影响多个服务，后续应逐步替换为纯 DTO。

---

## 12. 内部接口和外部接口

### 外部接口

给管理后台或小程序调用，通过网关进入，例如：

```text
/user/**
/coupon/**
/restaurant-order/**
/dish/**
```

### 内部接口

只给服务间调用：

```text
/inner/user/**
/inner/coupon/**
/inner/pay/**
```

当前网关会阻止外部经 8080 访问 `/inner/**`。但这并不代表内部接口天然安全。

部署时必须保证：

- 9001～9008 业务端口不直接暴露到公网。
- 防火墙、安全组或容器网络只允许网关和可信服务访问。
- 后续给内部调用增加服务身份认证、签名或 mTLS。
- 不能仅依靠 `@SaIgnore` 和“别人不知道端口”保护扣余额、加积分、核销券等接口。

---

## 13. 配置文件怎么写

不同服务可以有自己的 `application.yml`，但密码和密钥不要直接写进源码。

推荐写成环境变量：

```yaml
spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/hongtu}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      password: ${REDIS_PASSWORD:}
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
```

需要外置的内容包括：

- MySQL、Redis、RabbitMQ 密码。
- JWT 密钥。
- 邮箱授权码。
- 短信平台密钥。
- 七牛云密钥。
- 微信和支付宝商户密钥。
- 各环境的域名、回调地址和上传目录。

当前项目配置文件中已经存在明文凭据。正式部署前应移出代码仓库并轮换相关密钥。

另外，`localhost` 只适合所有组件运行在同一台机器的本地开发。如果以后用 Docker 或分机器部署，容器中的 `localhost` 只指向容器自己，应改成服务名、内网域名或环境变量。

---

## 14. 新增普通 CRUD 的项目内清单

假设要给餐厅服务新增“菜品标签”。

1. 确认表和业务属于 `restaurant-service`。
2. 在该服务创建 Entity。
3. 创建 Mapper。
4. 创建 Service 接口和实现。
5. 在 Service 中写业务校验和事务。
6. 创建请求 DTO，不要让前端直接控制所有 Entity 字段。
7. 创建返回 VO。
8. 创建 Controller。
9. 添加参数校验、权限注解和统一异常处理。
10. 如果 Controller 使用新的根路径，修改网关路由。
11. 编写 Service 单元测试和接口集成测试。

如果整个功能不访问其他服务，到这里就结束了，写法与单体 CRUD 基本相同。

---

## 15. 新增跨服务功能的项目内清单

1. 明确数据归属，选择服务提供方。
2. 在提供方 Service 中实现业务，不直接暴露 Mapper 语义。
3. 在提供方增加 `/inner/**` Controller。
4. 在 `liteShop-api` 增加请求/响应 DTO。
5. 在对应 Feign Client 中增加方法。
6. 调用方使用 `FeignResultUtil.unwrap/check`。
7. 检查调用方启动类是否启用 Feign。
8. 查询调用设置超时；写调用设计幂等键。
9. 涉及多个服务写操作时，画出失败点和补偿动作。
10. 编写契约测试，验证路径、参数名和返回类型一致。
11. 验证被调用服务停机、超时、重复请求时的行为。

---

## 16. 常见错误写法

### 错误一：因为共库就跨服务注入 Mapper

```java
// restaurant-service 中
@Resource
private SysUserMapper sysUserMapper;
```

这会让服务边界失效，以后分库时需要大改。

### 错误二：跨服务 JOIN

```sql
SELECT o.*, u.nickname
FROM restaurant_order o
JOIN sys_user u ON o.user_id = u.id;
```

即使现在能运行，也不应新增这种 SQL。推荐订单服务先查询订单，再批量调用用户服务查询所需的用户摘要，避免逐条调用造成 N+1 请求。

### 错误三：忽略 `Result.error`

```java
// 错误：HTTP 200 但业务可能失败
userFeignClient.addUserPoints(userId, points);
```

应该写：

```java
FeignResultUtil.check(userFeignClient.addUserPoints(userId, points));
```

### 错误四：认为远程写操作会随本地事务回滚

Feign 调用不会加入当前 Spring 本地事务。

### 错误五：补偿失败只记录日志

资金、优惠券、库存等重要业务必须有可重试记录和对账任务。

### 错误六：在循环里逐个调用 Feign

```java
for (Long userId : userIds) {
    userFeignClient.getUserById(userId);
}
```

应提供批量接口，减少网络请求次数。

### 错误七：把服务端口全部开放到公网

外部只应访问网关和必要的回调入口，业务服务端口应处于受保护网络。

---

## 17. 开发和启动方式

本地推荐启动顺序：

```text
MySQL / Redis / RabbitMQ
        ↓
Nacos
        ↓
user-service
        ↓
当前功能依赖的其他服务
        ↓
gateway
```

常见最小启动组合：

| 正在开发 | 至少启动 |
|---|---|
| 用户 CRUD | `user-service` |
| 优惠券 | `coupon-service`、`user-service` |
| 抽奖 | `lottery-service`、`user-service`、`coupon-service`、RabbitMQ |
| 订单 | `restaurant-service`、`user-service`、`coupon-service`、`pay-service` |
| 联调前端 | 相关业务服务 + `gateway` |

前端统一请求 `http://localhost:8080`。直接访问 900x 端口只适合本地调试，不应成为正式部署方式。

---

## 18. 测试时要比单体多测什么

普通 CRUD 测试与以前相同。跨服务功能还要测试：

- Feign 返回成功。
- Feign 返回 `Result.error`。
- 对方服务超时或不可用。
- 同一个写请求重复发送两次。
- 远端成功、本地失败。
- 本地成功、消息发送失败。
- 消息重复消费。
- 补偿执行失败后能否再次重试。

本项目目前没有测试文件。建议优先给以下链路补测试：

1. 下单并核销多张优惠券。
2. 余额支付和余额退款。
3. 桌位结算。
4. 抽奖发积分。
5. 抽奖发优惠券及 MQ 重复消费。
6. 微信、支付宝回调的重复通知。

---

## 19. 你当前最合适的学习与改造顺序

### 第一阶段：共库，但严格遵守服务边界

- 继续使用旧 SQL。
- 每张表只允许所属服务操作。
- 跨服务查询统一用 Feign。
- 不再新增跨服务 Mapper 和 JOIN。
- 学会本地启动和通过网关联调。

### 第二阶段：把跨服务写操作做可靠

- 给余额、优惠券、积分接口增加幂等键。
- 将余额更新改为原子 SQL。
- 给订单、扣款、核销设计明确状态。
- 增加补偿记录和对账任务。
- 修复 MQ 异常吞掉和消息丢失问题。

### 第三阶段：提高可维护性

- API Entity 改为纯 DTO。
- 配置外置并区分 dev/test/prod。
- 增加 Feign 超时、重试和熔断策略。
- 增加日志 traceId、健康检查和指标监控。
- 增加自动化测试。

### 第四阶段：再考虑分库

当代码已经做到“服务 A 不直接操作服务 B 的表”后，分库主要变成：

- 移动表。
- 修改各服务数据源。
- 删除跨库外键。
- 处理历史跨表查询。
- 完善最终一致性。

这时分库会比现在直接拆安全得多。

---

## 20. 每次写代码前的十个问题

1. 这个功能属于哪个服务？
2. 当前代码操作的表是否属于这个服务？
3. 是否错误地注入了另一个服务的 Service 或 Mapper？
4. 跨服务调用应该同步用 Feign，还是异步用 RabbitMQ？
5. Feign 失败时当前接口应该怎么返回？
6. 写操作重复执行会不会重复扣款、扣库存或发券？
7. 本地成功、远端失败时如何处理？
8. 远端成功、本地失败时如何补偿？
9. 新的外部路径是否已经配置网关路由和权限？
10. 是否写了成功、失败、超时、重复请求测试？

如果这十个问题都能回答清楚，就已经从“单体 CRUD 思维”进入了“微服务业务边界与失败处理思维”。

