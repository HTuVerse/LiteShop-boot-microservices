# 积分兑换服务

本服务保存租户的兑换商品、库存和兑换订单。商品支持纯积分、积分加现金、纯现金三种定价，目前均关联已上架的优惠券模板，不支持实物配送。现金部分使用租户的微信小程序支付账户。

## 部署

1. 依次执行 `sql/vertical-split/84_points_mall.sql` 与 `sql/vertical-split/85_mall_mixed_payment.sql`，创建 `liteshop_mall` 库并扩展三种价格、订单支付信息及幂等字段。已运行 56 号迁移的环境可以直接执行 84 号迁移。
2. 将 `nacos-config/liteShop-mall-service.example.yml` 发布为 Nacos Data ID `liteShop-mall-service.yml`，并重新发布网关配置。数据库连接可通过 `MYSQL_URL_MALL` 配置。
3. 启动或重启商城、用户、优惠券及网关服务。积分中心使用 `/points-mall/**`，后台菜单权限码为 `marketing:points-mall`。

`POST /points-mall/exchange` 必须携带每次用户兑换意图唯一的 `requestKey`。同一会员重复提交同一键会返回原订单。现金订单进入 `WAIT_PAY` 后，会员调用 `/points-mall/orders/{id}/wechat-pay` 获取微信支付参数；支付回调或 `/points-mall/orders/{id}/status` 主动查单确认付款后才发券。待支付订单 30 分钟过期，`/points-mall/orders/{id}/cancel` 可主动取消。失败时按订单幂等退回积分、已支付金额并恢复库存。优惠券发放后的使用规则沿用原优惠券服务。

租户的微信支付账户须配置 HTTPS 公网通知域名。商城使用同一域名下的 `/points-mall/wechat-pay/notify/{tenantId}` 和 `/points-mall/wechat-pay/refund-notify/{tenantId}`；网关须公开转发这两个 POST 路由。退款使用固定商户退款单号，通知和定时任务都会核对原订单、金额与商户。
