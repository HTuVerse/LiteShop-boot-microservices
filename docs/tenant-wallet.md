# 租户会员储值

## 数据归属

储值档位 `wallet_recharge_plan`、充值订单 `wallet_recharge_order`、会员余额 `sys_user` 和余额流水 `user_surplus_record` 均位于 `liteshop_user`。先执行 `sql/vertical-split/86_tenant_wallet.sql`，再部署 user-service、网关、管理端和小程序。生产网关将 `/wallet/**` 路由至 user-service，示例见 `nacos-config/liteShop-gateway.example.yml`。

## 支付与入账

- 管理端维护租户档位的实付额、赠送额、角标、排序与启用状态。客户端只提交档位 ID 和幂等请求键，订单保存金额快照。
- 当前只接入微信小程序支付。user-service 使用租户自己的微信商户配置调用 pay-service；回调先由 pay-service 验签，再核对租户、商户订单号、金额、币种和附加数据。客户端无法声明支付成功。
- 支付确认后订单转为 `PAID`，用户服务在本地事务中锁定会员行，以 `W{订单ID}` 为唯一流水键把本金与赠送额一次入账，再把订单转为 `SUCCESS`。未完成入账的 `PAID` 订单定时重试。过期未支付订单先查单、再关单。
- 余额字段 `surplus_money` 是可用总额，`gift_money` 是其中剩余赠送部分，本金余额为两者之差。既有会员余额迁移后全部视为本金。

## 余额支付分摊

每次消费以前一刻的余额为基准，按 `赠送余额 / 总余额` 计算赠送出资，四舍五入到分；本金出资为支付金额减去赠送出资。若本次使用全部余额，则两部分同时归零，消除逐笔取整尾差。消费流水记录实际使用的赠送金额；关联原支付订单的退款按原消费拆分比例恢复两部分，最后一笔退款吸收尾差。人工充值和无原支付单的余额增加计入本金。

## 接口

- 管理端：`GET/POST /wallet/admin/plans`、`PUT/DELETE /wallet/admin/plans/{id}`、`GET /wallet/admin/orders`，权限 `marketing:wallet`。
- 小程序：`GET /wallet/plans`、`GET /wallet/balance`、`POST /wallet/orders`、`GET /wallet/my-orders`、`POST /wallet/orders/{id}/wechat-pay`、`GET /wallet/orders/{id}/status`。
- 支付回调：`POST /wallet/wechat-pay/notify/{tenantId}`，仅供微信服务器调用。
