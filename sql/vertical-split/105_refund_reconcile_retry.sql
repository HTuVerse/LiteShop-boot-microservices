-- 退款渠道故障时使用持久化退避和人工核对标记，避免每分钟无限重试。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，逐列检查以便安全重跑。
USE `liteshop_restaurant`;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund' AND COLUMN_NAME = 'retry_count') = 0,
    'ALTER TABLE `order_refund` ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT ''渠道异常后的自动对账重试次数''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund' AND COLUMN_NAME = 'next_retry_at') = 0,
    'ALTER TABLE `order_refund` ADD COLUMN `next_retry_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''下次允许自动对账的时间''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund' AND COLUMN_NAME = 'review_required') = 0,
    'ALTER TABLE `order_refund` ADD COLUMN `review_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否需要人工核对：0否，1是''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund' AND COLUMN_NAME = 'last_error') = 0,
    'ALTER TABLE `order_refund` ADD COLUMN `last_error` VARCHAR(500) NULL COMMENT ''最近一次自动对账失败原因''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
