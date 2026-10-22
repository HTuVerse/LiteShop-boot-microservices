-- 已部署储值订单增加渠道锁；新部署由 86_tenant_wallet.sql 创建此字段。
ALTER TABLE `wallet_recharge_order`
  ADD COLUMN `payment_channel` varchar(10) NULL COMMENT '首次发起的支付渠道：WECHAT或ALIPAY' AFTER `out_trade_no`;

-- 旧版储值仅支持微信，保留历史待支付订单的微信查单能力。
UPDATE `wallet_recharge_order` SET `payment_channel`='WECHAT' WHERE `payment_channel` IS NULL;
