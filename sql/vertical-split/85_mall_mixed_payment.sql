-- 积分商城三种定价：纯积分、积分加现金、纯现金。先执行 84 号迁移。
USE `liteshop_mall`;

ALTER TABLE `mall_product`
  DROP CHECK `chk_mall_product_price`,
  ADD COLUMN `pricing_mode` varchar(20) NOT NULL DEFAULT 'POINTS' COMMENT 'POINTS/POINTS_CASH/CASH' AFTER `points_price`,
  ADD COLUMN `cash_price` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '现金售价，元' AFTER `pricing_mode`;
ALTER TABLE `mall_product`
  ADD CONSTRAINT `chk_mall_product_price` CHECK (`points_price` >= 0 AND `cash_price` >= 0
    AND ((`pricing_mode`='POINTS' AND `points_price`>0 AND `cash_price`=0)
      OR (`pricing_mode`='POINTS_CASH' AND `points_price`>0 AND `cash_price`>0)
      OR (`pricing_mode`='CASH' AND `points_price`=0 AND `cash_price`>0)));

ALTER TABLE `mall_exchange_order`
  ADD COLUMN `pricing_mode` varchar(20) NOT NULL DEFAULT 'POINTS' COMMENT '下单时定价方式' AFTER `points_cost`,
  ADD COLUMN `cash_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '下单时现金金额，元' AFTER `pricing_mode`,
  ADD COLUMN `payment_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/UNPAID/PAID/REFUNDING/REFUNDED' AFTER `cash_amount`,
  ADD COLUMN `pay_out_trade_no` varchar(64) NULL COMMENT '微信商户订单号' AFTER `payment_status`,
  ADD COLUMN `payment_txn_id` varchar(64) NULL COMMENT '微信交易号' AFTER `pay_out_trade_no`,
  ADD COLUMN `refund_no` varchar(64) NULL COMMENT '商户退款单号' AFTER `payment_txn_id`,
  ADD COLUMN `paid_at` datetime NULL COMMENT '现金支付时间' AFTER `refund_no`,
  ADD COLUMN `expires_at` datetime NULL COMMENT '待支付截止时间' AFTER `paid_at`;
ALTER TABLE `mall_exchange_order`
  ADD UNIQUE KEY `uk_tenant_pay_trade` (`tenant_id`,`pay_out_trade_no`),
  ADD UNIQUE KEY `uk_tenant_refund` (`tenant_id`,`refund_no`),
  ADD KEY `idx_tenant_wait_pay` (`tenant_id`,`status`,`expires_at`);
ALTER TABLE `mall_exchange_order`
  ADD CONSTRAINT `chk_mall_order_price` CHECK (`points_cost` >= 0 AND `cash_amount` >= 0);
