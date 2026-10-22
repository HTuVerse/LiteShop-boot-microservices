-- 先执行本迁移，再部署依赖新字段的应用版本。MySQL 8 使用元数据判断实现重复执行安全。
USE `liteshop_restaurant`;

SET @had_committed = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'local_outbox' AND COLUMN_NAME = 'committed');
SET @ddl = IF(@had_committed = 0,
    'ALTER TABLE `local_outbox` ADD COLUMN `committed` tinyint NOT NULL DEFAULT 0 COMMENT ''外层业务事务是否已提交：0否，1是'' AFTER `status`',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 升级前的事件没有可靠提交标记，保留给人工核对，避免错误自动补偿。
UPDATE `local_outbox` SET `status` = 'NEEDS_REVIEW'
WHERE @had_committed = 0 AND `status` IN ('SENT', 'PENDING', 'UNKNOWN')
  AND `event_type` <> 'MEMBER_REWARD_SYNC';

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'local_outbox'
      AND INDEX_NAME = 'idx_local_outbox_reconcile') = 0,
    'ALTER TABLE `local_outbox` ADD INDEX `idx_local_outbox_reconcile` (`status`, `updated_at`)',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

USE `liteshop_coupon`;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon_message_receipt'
      AND COLUMN_NAME = 'lottery_notified_at') = 0,
    'ALTER TABLE `coupon_message_receipt` ADD COLUMN `lottery_notified_at` datetime NULL COMMENT ''抽奖服务领取通知确认时间，空表示待重试'' AFTER `created_at`',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon_message_receipt'
      AND INDEX_NAME = 'idx_lottery_notice') = 0,
    'ALTER TABLE `coupon_message_receipt` ADD INDEX `idx_lottery_notice` (`lottery_notified_at`, `created_at`)',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
