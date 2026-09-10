-- 公告发布人姓名快照增量升级。
-- 适用于已经执行过 26_message_announcements.sql 的环境；可重复执行。
SET NAMES utf8mb4;
USE `liteshop_message`;

SET @publisher_name_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_announcement'
    AND COLUMN_NAME = 'publisher_name'
);
SET @publisher_name_ddl = IF(
  @publisher_name_exists = 0,
  'ALTER TABLE `sys_announcement` ADD COLUMN `publisher_name` VARCHAR(100) NULL COMMENT ''发布人姓名快照'' AFTER `publisher_id`',
  'SELECT 1'
);
PREPARE publisher_name_stmt FROM @publisher_name_ddl;
EXECUTE publisher_name_stmt;
DEALLOCATE PREPARE publisher_name_stmt;

UPDATE `sys_announcement`
SET `publisher_name` = CONCAT('用户 ', `publisher_id`)
WHERE `publisher_name` IS NULL OR TRIM(`publisher_name`) = '';

ALTER TABLE `sys_announcement`
  MODIFY COLUMN `publisher_name` VARCHAR(100) NOT NULL COMMENT '发布人姓名快照';
