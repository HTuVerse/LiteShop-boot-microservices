# 收银台付款码支付（微信被扫 + 支付宝条码）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让收银员用扫码枪扫顾客付款码即可完成微信/支付宝收款，打通「商家被扫」支付链路，与现有「小程序在线主动支付」并存。

**Architecture:** 支付宝侧复用已引入的 `alipay-sdk-java`（`alipay.trade.pay` scene=bar_code），零新依赖；微信付款码走 v2 接口、官方 `wechatpay-java` 不支持，引入 `com.github.binarywang:weixin-java-pay`。pay-service 新增「付款码」服务方法与内部接口，按租户构建并缓存客户端（沿用 `TenantPaymentClientFactory` 指纹缓存模式）；config-service 新增「微信 v2 API 密钥」密文字段；restaurant-service 把占位的 `cashierScanPay` 落地为「下单 → 被扫 → 轮询/撤单 → 标记已支付」。

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Cloud OpenFeign, MyBatis-Plus, `alipay-sdk-java 4.40.379.ALL`, `wechatpay-java 0.2.17`（存量）, `weixin-java-pay 4.7.5.B`（新增，Maven 中央仓库最新稳定版）。

**Spec:** 本文件头部的 Goal/Architecture 即为需求来源（用户口述：两种支付模式，其一为收银扫顾客付款码）。

## Global Constraints

- 金额一律以服务端重算结果为准，付款码场景同样经过 `CashierAmountGuard.requireServerPayable(...)`，不信任前端金额。
- 租户支付隔离不回退全局商户：缺少可用账户/密钥时必须抛 `BusinessException`，不得静默降级（沿用 `TenantPaymentAccountGuard`）。
- 所有新增密钥只存密文（`AiModelKeyCipher` AES/GCM），数据库与管理端均不出现明文；`TenantPaymentAccountView` 里新密钥字段标 `@JsonProperty(access = WRITE_ONLY)`。
- 新增 Controller/Service/Entity/DTO/枚举/配置类需按 `AGENTS.md` 补简洁中文类注释与接口注释；SQL 字段带中文 `COMMENT`。
- 付款码有效期约 1 分钟：`authCode` 为一次性，重试下单必须复用同一 `idempotencyKey`，不得用同一 `authCode` 重复发起。

## Review Focus

- **付款码过期/重复扫（authCode）**：过期或已用的付款码，渠道返回失败，`cashierScanPay` 应返回明确「付款码无效/已过期，请让顾客刷新」而非笼统失败；同一 `idempotencyKey` 重试不重复建单。测试归属 Task 8。
- **USERPAYING / 10003 等待输入密码后轮询超时**：轮询结束仍未支付必须撤单（微信 `reverseOrder` / 支付宝 `trade.cancel`），避免顾客稍后补输密码造成「订单未支付但实际扣款」的资损。测试归属 Task 5、Task 6。
- **微信 v2 密钥未配置即发起付款码**：`requireWeChatMicroPay` 必须抛错，不能回退到 JSAPI 的 v3 参数。测试归属 Task 4。
- **渠道支付成功但本地标记失败**：成功后 `markPaidIfCapturable` 更新行数非 1（并发/状态变化）时的处理，避免重复收款或丢状态。测试归属 Task 8。
- **金额小数位**：付款码金额同样可能出现非两位小数输入，服务端换算（微信分、支付宝两位元）须拒绝非法精度而不是静默取整。测试归属 Task 5、Task 6。

---

## Task 1: 引入 WxJava 付款码依赖

**Files:**
- Modify: `liteShop-dependencies/pom.xml:41-50`（properties 增版本号）、`:174-192`（dependencyManagement 增条目）
- Modify: `liteShop-pay-service/pom.xml:64-68`（dependencies 增条目）

**Interfaces:**
- Produces: 编译期可用 `com.github.binarywang.wxpay.service.WxPayService`、`com.github.binarywang.wxpay.config.WxPayConfig`、`com.github.binarywang.wxpay.bean.request.WxPayMicropayRequest`、`...result.WxPayMicropayResult`。

- [ ] **Step 1: 在 BOM 增加版本属性与依赖管理**
  - `properties` 增 `<weixin-java-pay.version>4.7.5.B</weixin-java-pay.version>`（Maven 中央仓库最新稳定版，已核实）。
  - `dependencyManagement` 增 `com.github.binarywang:weixin-java-pay:${weixin-java-pay.version}`。
- [ ] **Step 2: 在 pay-service 声明依赖**（紧邻现有 `wechatpay-java` 条目，不写 version，由 BOM 管控）。
- [ ] **Step 3: 验证可解析编译**
  - Run: `mvn -q -pl liteShop-pay-service -am dependency:resolve`
  - Expected: 成功，列出 `weixin-java-pay`。
- [ ] **Step 4: Commit** — `build: 引入 weixin-java-pay 用于微信付款码支付`

---

## Task 2: 持久化微信 v2 API 密钥（迁移 + 实体）

**Files:**
- Create: `sql/vertical-split/53_tenant_payment_account_v2_key.sql`
- Modify: `liteShop-config-service/src/main/java/org/hongtu/config/DTO/TenantPaymentAccount.java`

**Interfaces:**
- Produces: `TenantPaymentAccount.getApiV2KeyCiphertext()/setApiV2KeyCiphertext(String)`；表 `tenant_payment_account.api_v2_key_ciphertext`。

- [ ] **Step 1: 写迁移脚本**
  - `USE liteshop_config;` + `ALTER TABLE tenant_payment_account ADD COLUMN api_v2_key_ciphertext TEXT NULL COMMENT '加密后的微信APIv2密钥（付款码v2接口用），支付宝为空';`
  - 说明：现有 TEXT 列不带 DEFAULT，新增列用 `NULL` 兼容存量行，代码按空串处理。
- [ ] **Step 2: 实体加字段**
  - 在 `apiV3KeyCiphertext` 后新增，带 `@ToString.Exclude` 与中文注释 `/** 加密后的微信APIv2密钥（付款码）。 */`。
- [ ] **Step 3: 验证编译** — Run: `mvn -q -pl liteShop-config-service -am compile` Expected: PASS
- [ ] **Step 4: Commit** — `feat(config): tenant_payment_account 增加微信APIv2密钥密文字段`

---

## Task 3: config-service 读写与校验 v2 密钥

**Files:**
- Modify: `liteShop-config-service/src/main/java/org/hongtu/config/service/TenantPaymentAccountService.java`
- Modify: `liteShop-api/src/main/java/org/hongtu/api/dto/TenantPaymentAccountView.java`
- Modify: `liteShop-api/src/main/java/org/hongtu/api/dto/TenantPaymentRuntimeDTO.java`
- Test: `liteShop-config-service/src/test/java/org/hongtu/config/service/TenantPaymentAccountServiceTest.java`

**Interfaces:**
- Consumes: `AiModelKeyCipher.encrypt/decrypt`、`mergeSecret(...)`（Task 内复用现有私有方法）。
- Produces: `TenantPaymentRuntimeDTO.getApiV2Key()/setApiV2Key(String)`；`TenantPaymentAccountView.apiV2KeyConfigured / apiV2Key(write-only) / clearApiV2Key`；`runtime()` 对 WECHAT 渠道返回解密后的 `apiV2Key`。

- [ ] **Step 1: 写失败测试**
  - `saveWeChatWithV2KeyThenRuntimeReturnsDecrypted`: 保存 WECHAT（含 `apiV2Key="12345678901234567890123456789012"`）后，`runtime(tenantId,"WECHAT").getApiV2Key()` 等于原文。
  - `saveWeChatWithoutV2KeyKeepsExistingCiphertext`: 二次保存不传 v2 key 时不清空既有密文（断言 `view.isApiV2KeyConfigured()` 仍为 true）。
- [ ] **Step 2: 运行确认失败** — Run: `mvn -q -pl liteShop-config-service test -Dtest=TenantPaymentAccountServiceTest` Expected: FAIL（方法/字段不存在）
- [ ] **Step 3: 实现**
  - `TenantPaymentAccountView` 增三字段（参照现有 `apiV3Key*` 的注解风格）。
  - `TenantPaymentRuntimeDTO` 增 `@ToString.Exclude private String apiV2Key;`。
  - `save()`：WECHAT 时 `current.setApiV2KeyCiphertext(mergeSecret(current.getApiV2KeyCiphertext(), submitted.getApiV2Key(), submitted.getClearApiV2Key()))`，ALIPAY 时置 `""`。
  - `runtime()`：WECHAT 时 `runtime.setApiV2Key(cipher.decrypt(account.getApiV2KeyCiphertext()))`，否则 `""`。
  - `view()`：`view.setApiV2KeyConfigured(account != null && StringUtils.hasText(account.getApiV2KeyCiphertext()))`。
  - `requireReady()`：v2 密钥对「启用」保持**可选**（JSAPI 不需要）；若提交了 v2 key 则校验明文 32 字节，否则抛「微信APIv2密钥必须为32字节」。
- [ ] **Step 4: 运行确认通过** — Run: 同 Step 2 Expected: PASS
- [ ] **Step 5: Commit** — `feat(config): 支持微信APIv2密钥的保存、解密下发与校验`

---

## Task 4: pay-service 付款码客户端与守卫

**Files:**
- Modify: `liteShop-pay-service/src/main/java/org/hongtu/service/TenantPaymentAccountGuard.java`
- Modify: `liteShop-pay-service/src/main/java/org/hongtu/service/TenantPaymentClientFactory.java`
- Test: `liteShop-pay-service/src/test/java/org/hongtu/service/TenantPaymentAccountGuardTest.java`

**Interfaces:**
- Consumes: `TenantPaymentRuntimeDTO.getApiV2Key()`（Task 3）。
- Produces:
  - `TenantPaymentAccountGuard.requireWeChatMicroPay(TenantPaymentRuntimeDTO): TenantPaymentRuntimeDTO`（校验 base + `mchId` + `apiV2Key` 非空，32 字节）。
  - `TenantPaymentClientFactory.wxMicroPay(Long tenantId): com.github.binarywang.wxpay.service.WxPayService`（按指纹缓存）。

- [ ] **Step 1: 写失败测试（Guard）**
  - `requireWeChatMicroPayRejectsMissingV2Key`: base + appId + privateKey + mchId 但无 apiV2Key → 抛 `BusinessException`。
  - `requireWeChatMicroPayAcceptsComplete`: 再补 `apiV2Key`（32 字节）→ 不抛。
- [ ] **Step 2: 运行确认失败** — Run: `mvn -q -pl liteShop-pay-service test -Dtest=TenantPaymentAccountGuardTest` Expected: FAIL
- [ ] **Step 3: 实现**
  - Guard 新增静态方法，复用 `requireBase(...)`，再校验 `mchId` 与 `apiV2Key`（`getBytes(UTF_8).length == 32`）。
  - `TenantPaymentClientFactory`：
    - 新增 `ConcurrentHashMap<String, WxPayService> wxMicroPayClients`。
    - `cacheKey(...)` 的指纹 material 追加 `value(account.getApiV2Key())`（确保改 v2 key 即换客户端）。
    - `wxMicroPay(tenantId)`：`load+requireWeChatMicroPay` → 构建 `WxPayConfig`（`setAppId/setMchId/setMchKey(apiV2Key)`，`setUseSandboxEnv(false)`）→ `new WxPayServiceImpl()` + `setConfig(config)`；`computeIfAbsent` 缓存；`evictStale` 清旧；异常包成「微信付款码客户端初始化失败」。
- [ ] **Step 4: 运行确认通过** — Run: 同 Step 2 Expected: PASS
- [ ] **Step 5: Commit** — `feat(pay): 新增微信付款码守卫与按租户 WxPayService 客户端`

---

## Task 5: pay-service 微信付款码服务（下单/查单/撤单）

**Files:**
- Modify: `liteShop-pay-service/src/main/java/org/hongtu/service/WeChatPayService.java`
- Modify: `liteShop-pay-service/src/main/java/org/hongtu/service/impl/WeChatPayServiceImpl.java`
- Test: `liteShop-pay-service/src/test/java/org/hongtu/service/WeChatMicroPayServiceTest.java`

**Interfaces:**
- Consumes: `TenantPaymentClientFactory.wxMicroPay(tenantId)`（Task 4）。
- Produces（返回 `Map<String,Object>`，统一字段 `state ∈ {SUCCESS, USERPAYING, FAIL}`、`transactionId`、`outTradeNo`、`msg`）：
  - `createMicroPay(Long tenantId, String outTradeNo, String description, BigDecimal totalAmountYuan, String authCode): Map<String,Object>`
  - `queryMicroPay(Long tenantId, String outTradeNo): Map<String,Object>`
  - `reverseMicroPay(Long tenantId, String outTradeNo): Map<String,Object>`（返回 `recall` 布尔，微信 `reverseOrder` 的 `isRecall`）

- [ ] **Step 1: 写失败测试（mock `WxPayService`）**
  - `microPaySuccessMapsState`: `microOrder` 返回 `result_code=SUCCESS` → `state=SUCCESS`，`transactionId` 透传。
  - `microPayUserPayingMapsState`: `microOrder` 抛 `WxPayException(err_code=USERPAYING)`（或结果码 USERPAYING）→ `state=USERPAYING`。
  - `microPayRejectsNonTwoDecimalAmount`: `totalAmountYuan` 为三位小数 → 抛 `BusinessException`（金额换算分用 `movePointRight(2).setScale(0, UNNECESSARY)`，复用现有风格）。
  - `reverseMicroPayReturnsRecallFlag`: `reverseOrder` 返回 `recall=Y` → map `recall=true`。
- [ ] **Step 2: 运行确认失败** — Run: `mvn -q -pl liteShop-pay-service test -Dtest=WeChatMicroPayServiceTest` Expected: FAIL
- [ ] **Step 3: 实现**
  - 接口加三方法并补注释；impl 用 `clients.wxMicroPay(tenantId)`。
  - `createMicroPay`：构建 `WxPayMicropayRequest`（`outTradeNo/body/totalFee(分,int)/authCode/spbillCreateIp`），`spbillCreateIp` 用占位内网 IP 常量；捕获 `WxPayException`，`USERPAYING/SYSTEMERROR` → `state=USERPAYING`，其余 → `state=FAIL`。
  - `queryMicroPay`：`WxPayService.queryOrder(null, outTradeNo)`，按 `tradeState/trade_state` 映射 SUCCESS/USERPAYING/FAIL。
  - `reverseMicroPay`：`WxPayService.reverseOrder(...)`。
- [ ] **Step 4: 运行确认通过** — Run: 同 Step 2 Expected: PASS
- [ ] **Step 5: Commit** — `feat(pay): 微信付款码下单/查单/撤单`

---

## Task 6: pay-service 支付宝条码支付服务（下单/撤单）

**Files:**
- Modify: `liteShop-pay-service/src/main/java/org/hongtu/service/AlipayService.java`
- Modify: `liteShop-pay-service/src/main/java/org/hongtu/service/impl/AlipayServiceImpl.java`
- Test: `liteShop-pay-service/src/test/java/org/hongtu/service/AlipayBarCodeServiceTest.java`

**Interfaces:**
- Consumes: `TenantPaymentClientFactory.alipay(tenantId)`（存量）。
- Produces（`Map<String,Object>`，`state ∈ {SUCCESS, WAITING, FAIL}`、`tradeNo`、`outTradeNo`、`msg`）：
  - `createBarCodePay(Long tenantId, String outTradeNo, String subject, BigDecimal totalAmountYuan, String authCode): Map<String,Object>`
  - `cancelTrade(Long tenantId, String outTradeNo): Map<String,Object>`（返回 `retryFlag`、`action`）

- [ ] **Step 1: 写失败测试（mock `AlipayClient`）**
  - `barCodeSuccessMapsState`: `execute(AlipayTradePayRequest)` 返回 `isSuccess=true` → `state=SUCCESS`，`tradeNo` 透传。
  - `barCodeWaitingOnSubCode10003`: `subCode=ACQ.PAYMENT_AUTH_CODE_INVALID`? → 用等待码 `subCode` 判定；具体：`code=10003` 或 `subCode` 以等待语义返回 → `state=WAITING`。（按支付宝 `alipay.trade.pay` 文档：`10003` 表示「等待用户付款」需轮询。）
  - `cancelTradeMapsRetryFlag`: `execute(AlipayTradeCancelRequest)` 返回 `retryFlag=N` → map。
- [ ] **Step 2: 运行确认失败** — Run: `mvn -q -pl liteShop-pay-service test -Dtest=AlipayBarCodeServiceTest` Expected: FAIL
- [ ] **Step 3: 实现**
  - `createBarCodePay`：`AlipayTradePayRequest`，`bizContent` 含 `out_trade_no / scene=bar_code / auth_code / subject / total_amount(两位元 toPlainString)`；按 `response.getCode()`（`10000`→SUCCESS、`10003`→WAITING、其余→FAIL）映射。
  - `cancelTrade`：`AlipayTradeCancelRequest`，返回 `retry_flag/action`。
  - 金额精度：`setScale(2, UNNECESSARY)`，非两位抛 `ArithmeticException` → 捕获转 `BusinessException`。
- [ ] **Step 4: 运行确认通过** — Run: 同 Step 2 Expected: PASS
- [ ] **Step 5: Commit** — `feat(pay): 支付宝条码支付下单与撤单`

---

## Task 7: 内部接口与 Feign 契约

**Files:**
- Create: `liteShop-api/src/main/java/org/hongtu/params/WeChatMicroPayParam.java`
- Create: `liteShop-api/src/main/java/org/hongtu/params/AlipayBarCodePayParam.java`
- Modify: `liteShop-pay-service/src/main/java/org/hongtu/controller/InnerPayController.java`
- Modify: `liteShop-api/src/main/java/org/hongtu/api/client/PayFeignClient.java`

**Interfaces:**
- Produces（Controller + Feign 同签名同方法名，返回 `Result<Map<String,Object>>`）：
  - `createWeChatMicroPay(@RequestBody WeChatMicroPayParam)` → `POST /inner/pay/wechat/micropay`（`WeChatMicroPayParam{tenantId,outTradeNo,description,totalAmount,authCode}`）
  - `queryWeChatMicroPay(@RequestParam tenantId, @RequestParam outTradeNo)` → `GET /inner/pay/wechat/micropay/query`
  - `reverseWeChatMicroPay(@RequestParam tenantId, @RequestParam outTradeNo)` → `POST /inner/pay/wechat/micropay/reverse`
  - `createAlipayBarCodePay(@RequestBody AlipayBarCodePayParam)` → `POST /inner/pay/alipay/barcode`（`AlipayBarCodePayParam{tenantId,outTradeNo,subject,totalAmount,authCode}`）
  - `cancelAlipayTrade(@RequestParam tenantId, @RequestParam outTradeNo)` → `POST /inner/pay/alipay/cancel`
  - 支付宝查单复用存量 `queryAlipayTrade` → `GET /inner/pay/alipay/query`。

- [ ] **Step 1: 建两个 params DTO**（`@Data @Builder @NoArgsConstructor @AllArgsConstructor`，字段带中文注释，`authCode` 注明「顾客付款码，一次性」）。
- [ ] **Step 2: Controller 增 5 个方法**，委派 Task 5/6 服务方法，保持 `@SaIgnore` 与现有注释风格。
- [ ] **Step 3: PayFeignClient 增对应 5 个方法**（注解、参数名与 Controller 完全一致）。
- [ ] **Step 4: 验证编译** — Run: `mvn -q -pl liteShop-pay-service -am compile` Expected: PASS
- [ ] **Step 5: Commit** — `feat(pay): 暴露付款码内部接口与 Feign 契约`

---

## Task 8: 收银台 cashierScanPay 落地

**Files:**
- Modify: `liteShop-restaurant-service/src/main/java/org/hongtu/restaurant/DTO/CashierOrderRequest.java`（加 `authCode`）
- Modify: `liteShop-restaurant-service/src/main/java/org/hongtu/restaurant/controller/RestaurantOrderController.java:832-858`
- Modify: `liteShop-restaurant-service/src/main/java/org/hongtu/restaurant/service/impl/RestaurantOrderServiceImpl.java:1554`（`cashierScanPay`）
- Test: `liteShop-restaurant-service/src/test/java/org/hongtu/CashierScanPayTest.java`

**Interfaces:**
- Consumes: `PayFeignClient`（Task 7）、`CashierAmountGuard.requireServerPayable(...)`、`baseMapper.markPaidIfCapturable(...)` / `commitOrderSales(...)`（存量，见 `RestaurantOrderServiceImpl:3082-3088`）。
- Produces: `RestaurantOrderVO cashierScanPay(CashierOrderRequest request)` 返回已支付订单；`authCode` 为空或金额非法时抛 `BusinessException`。

- [ ] **Step 1: 写失败测试（mock `PayFeignClient`）**
  - `scanPayRequiresAuthCode`: `authCode` 空 → 抛 `BusinessException("请扫描顾客付款码")`。
  - `scanPaySuccessMarksPaid`: 渠道返回 `state=SUCCESS` → 订单 `paymentStatus=PAID`，`paymentNo=transactionId`。
  - `scanPayUserPayingThenPaidByPolling`: 首次 `USERPAYING`、查单第二次 `SUCCESS` → 最终 PAID。
  - `scanPayTimeoutReversesAndFails`: 轮询耗尽仍 `USERPAYING/WAITING` → 调用 reverse/cancel 且抛 `BusinessException`，订单不置 PAID。
  - `scanPayUsesServerAmountNotClientTotal`: 请求 `totalPrice` 被篡改时以服务端重算金额发起（断言传给 Feign 的金额为服务端值）。
- [ ] **Step 2: 运行确认失败** — Run: `mvn -q -pl liteShop-restaurant-service test -Dtest=CashierScanPayTest` Expected: FAIL
- [ ] **Step 3: 实现**
  - `CashierOrderRequest` 增 `/** 顾客付款码（扫码枪读取，一次性）。 */ private String authCode;`。
  - `cashierScanPay`（`@Transactional`，幂等键沿用 `cashierDirectOrder` 的校验）：
    1. 校验 `authCode`、`cartItems`、`paymentType ∈ {WECHAT, ALIPAY}`。
    2. 服务端定价重算 + `CashierAmountGuard.requireServerPayable(...)` 得应付金额；建单与明细（复用 `cashierDirectOrder` 的建单片段，`paymentStatus=UNPAID`、`paymentMode=ONLINE`）。
    3. 按渠道调 `payFeignClient.createWeChatMicroPay(...)` / `payFeignClient.createAlipayBarCodePay(...)`。
    4. `state=SUCCESS` → 直接标记已支付；`state ∈ {USERPAYING, WAITING}` → 轮询查单（`queryWeChatMicroPay` / `queryAlipayTrade`，固定次数 + 间隔，如 6 次 × 2s，常量定义），期间 SUCCESS 即落袋；超时 → `reverseWeChatMicroPay` / `cancelAlipayTrade` 并抛错。
    5. 成功后 `markPaidIfCapturable(...)` + `commitOrderSales(...)`，按 `printReceipt` 复用现有打印逻辑。
  - Controller：移除「暂未开放」返回，校验后委派服务，成功返回订单 + 支付结果 Map。
- [ ] **Step 4: 运行确认通过** — Run: 同 Step 2 Expected: PASS
- [ ] **Step 5: Commit** — `feat(cashier): 收银台付款码支付（微信被扫/支付宝条码）落地`

---

## Task 9: 回归与文档

**Files:**
- Modify: `docs/` 相关支付说明（如 `docs/multi-tenant-guide.md` 支付小节，补「微信付款码需配置 v2 密钥并开通付款码支付产品」）

- [ ] **Step 1: 全量测试** — Run: `mvn -q -pl liteShop-pay-service,liteShop-config-service,liteShop-restaurant-service -am test` Expected: PASS
- [ ] **Step 2: 文档补充**：v2 密钥配置位置、付款码产品开通前置条件、轮询/撤单行为、支付宝条码零新依赖说明。
- [ ] **Step 3: Commit** — `docs: 补充付款码支付配置与运行说明`

---

## 前端（liteS）后续（本计划范围外，另起）

收银台「扫码支付」入口当前前端亦为占位，需要：接入扫码枪输入 `authCode`、调用 `/restaurant-order/cashier/scanPay`、展示「支付中」轮询态与失败/撤单提示。建议作为独立前端计划实施，后端接口稳定后对接。
