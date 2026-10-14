-- 订单支付 P0 加固：记录库存预占状态，避免支付回调重复扣库存。
ALTER TABLE `restaurant_order`
    ADD COLUMN IF NOT EXISTS `stock_reserved` tinyint(1) NOT NULL DEFAULT 0
        COMMENT '是否已扣减门店库存：0否，1是'
        AFTER `payment_no`;

-- 既有已支付订单已执行过库存扣减，迁移时同步状态。
UPDATE `restaurant_order`
SET `stock_reserved` = 1
WHERE `payment_status` IN (1, 2)
  AND `status` <> 6;
