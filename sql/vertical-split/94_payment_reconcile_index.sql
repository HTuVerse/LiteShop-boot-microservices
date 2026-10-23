-- 支付回调丢失时，按订单主键分批补查未确认的线上渠道交易。
ALTER TABLE restaurant_order
  ADD INDEX idx_payment_reconcile (payment_mode, payment_status, payment_type, id);
