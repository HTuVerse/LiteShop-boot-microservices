# 餐厅订单与支付服务边界

- 餐厅订单服务校验订单、计算应付金额，并通过 `/inner/pay/create` 提交租户、订单号、渠道和支付场景；支付服务构造微信或支付宝交易参数、选择账户及网关、生成通知地址。
- 支付服务接收 `/wechatPay/**`、`/alipayPay/**` 通知，验签或解密后通过 `/inner/restaurant/payment/**` 提交结果；餐厅订单服务再次核对租户、订单、金额和退款单并完成状态流转。
- 商城和钱包的微信支付仍使用各自提供的回调地址。餐厅微信请求使用 `callbackOwner=RESTAURANT`，由支付服务读取租户支付账户中的回调地址。

## 部署与配置

将 `nacos-config/liteShop-gateway.example.yml` 中的 `pay-notify-service` 路由同步到实际运行的网关 Nacos 配置，并放在通用 `restaurant-service` 路由之前。支付账户中的回调地址必须是**自己的公网 HTTPS 网关域名**，支付宝为 `https://你的域名/alipayPay/notify`，微信为 `https://你的域名/wechatPay/notify`；支付服务会自动追加租户 ID。支付宝开放平台或沙箱网关域名不能作为商户回调地址。

同时部署更新后的 API 契约、支付服务和餐厅服务。支付宝沙箱开关由配置服务的 `TenantPaymentRuntimeDTO.sandboxEnabled` 传给支付服务；值缺失时支付服务拒绝创建交易，避免沙箱 AppID 误发正式网关。网关路由更新前，新的餐厅服务公开回调接口不再存在。
