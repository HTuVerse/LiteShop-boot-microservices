USE `liteshop_restaurant`;

-- 历史有效支付订单补齐支付时间，后续经营收入统一按支付日期归属。
UPDATE `restaurant_order`
SET `payment_time` = `created_at`
WHERE `payment_status` IN (1, 2, 3)
  AND `payment_time` IS NULL;

-- 历史取消订单补齐取消时间，取消原因统一按实际取消日期归属。
UPDATE `restaurant_order`
SET `cancel_time` = COALESCE(`updated_at`, `created_at`)
WHERE `status` = 6
  AND `cancel_time` IS NULL;

ALTER TABLE `restaurant_order`
  DROP INDEX `idx_store_payment_time`,
  ADD INDEX `idx_store_payment_time` (`tenant_id`, `store_id`, `payment_status`, `payment_time`),
  ADD INDEX `idx_store_cancel_time` (`tenant_id`, `store_id`, `status`, `cancel_time`);
