# 订单支付场景接口

顾客从 `POST /restaurant-order/issuePay` 发起支付。请求体使用服务端订单 `orderId`、`paymentType`（微信或支付宝）、`paymentScene`，订单金额由后端读取，不接受客户端金额。返回的 `paymentScene` 与请求一致，前端按下表调起支付。

| 渠道 | `paymentScene` | 额外请求字段 | 响应字段 | 前端动作 |
| --- | --- | --- | --- | --- |
| 微信 | `MINI_PROGRAM` | `openId` | `miniPayParams` | 小程序调起支付 |
| 微信 | `JSAPI` | `openId` | `miniPayParams` | 微信内网页调用 JSAPI；openid 必须属于配置的 appId |
| 微信 | `H5` | 无 | `payUrl` | 在微信浏览器外的手机浏览器跳转 |
| 微信 | `PC`、`FACE_TO_FACE` | 无 | `qrCode` | 绘制微信 Native 支付二维码 |
| 支付宝 | `MINI_PROGRAM` | `authCode` | `tradeNo` | 小程序收银台使用交易号 |
| 支付宝 | `JSAPI` | `authCode` | `tradeNo` | 支付宝内网页收银台使用交易号 |
| 支付宝 | `H5` | 可选 `returnUrl` | `payForm` | 在顶层页面提交支付宝手机网站支付表单 |
| 支付宝 | `PC` | 可选 `returnUrl` | `payForm` | 在顶层页面提交支付宝电脑网站支付表单 |
| 支付宝 | `FACE_TO_FACE` | 无 | `qrCode` | 绘制支付宝预创建二维码 |

支付宝 H5/PC 的 `payUrl` 暂时也返回同一 HTML 表单，兼容旧客户端；它不是 URL。`returnUrl` 只接受与请求 `Origin` 同源的 HTTPS 地址（本机开发可用 HTTP）。同步返回不代表支付成功，须使用订单支付状态接口或平台异步通知确认。

当面付的另一种方式是收银员扫描顾客付款码，沿用 `POST /restaurant-order/cashier/scanPay`。微信走付款码接口，支付宝走条码支付接口；支付中状态由既有查单和撤销流程处理。

支付服务内部接口由订单服务携带可信订单信息调用：微信 `/inner/pay/wechat/jsapi`、`/h5`、`/native`、`/micropay`；支付宝 `/inner/pay/alipay/mini`、`/jsapi`、`/wap`、`/page`、`/precreate`、`/barcode`。各渠道共用原有查单、关单、退款及验签回调。生产使用前，租户支付账户需开通对应产品，配置相应 appId/商户号、密钥及可公网访问的异步通知地址。
