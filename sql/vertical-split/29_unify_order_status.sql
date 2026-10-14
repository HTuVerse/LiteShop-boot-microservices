-- 统一订单履约状态。
-- 必须先执行本脚本，再部署移除旧“未支付(0)”订单状态的 restaurant-service。
-- 支付结果继续由 payment_status 独立表示，本脚本不改变任何支付数据。
USE liteshop_restaurant;

START TRANSACTION;

UPDATE restaurant_order
SET status = 1,
    updated_at = COALESCE(updated_at, NOW())
WHERE status = 0;

COMMIT;

ALTER TABLE restaurant_order
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1
    COMMENT '订单履约状态：1-已下单，2-已接单，3-制作中，4-制作完成，5-已完成，6-已取消',
    MODIFY COLUMN payment_status TINYINT NOT NULL DEFAULT 0
    COMMENT '支付状态：0-未支付，1-已支付，2-部分退款，3-全额退款，4-支付超时，5-取消支付，6-支付失败';

