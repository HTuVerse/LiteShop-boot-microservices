# 积分兑换服务

本服务保存租户的兑换商品、库存和兑换订单。目前商品只能关联已上架的优惠券模板，不支持实物配送。

## 部署

1. 执行 `sql/vertical-split/84_points_mall.sql`，创建 `liteshop_mall` 库并扩展积分和优惠券幂等字段。已运行 56 号迁移的环境可以直接执行 84 号迁移。
2. 将 `nacos-config/liteShop-mall-service.example.yml` 发布为 Nacos Data ID `liteShop-mall-service.yml`，并重新发布网关配置。数据库连接可通过 `MYSQL_URL_MALL` 配置。
3. 启动或重启商城、用户、优惠券及网关服务。积分中心使用 `/points-mall/**`，后台菜单权限码为 `marketing:points-mall`。

`POST /points-mall/exchange` 必须携带每次用户兑换意图唯一的 `requestKey`。同一会员重复提交同一键会返回原订单。服务通过订单状态与租约重试结果不明的扣积分/发券调用；失败时按订单幂等退回积分并恢复商品库存。优惠券发放后的使用规则沿用原优惠券服务。
