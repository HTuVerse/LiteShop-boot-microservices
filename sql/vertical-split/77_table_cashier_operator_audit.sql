USE `liteshop_restaurant`;

-- 兼容已使用最新建表脚本、或只执行过部分迁移的数据库；重复执行不会报 1060。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'restaurant_order' AND COLUMN_NAME = 'operator_id') = 0,
  'ALTER TABLE `restaurant_order` ADD COLUMN `operator_id` bigint NULL COMMENT ''下单操作人ID，与会员ID分离'' AFTER `user_id`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'restaurant_order' AND COLUMN_NAME = 'operator_name') = 0,
  'ALTER TABLE `restaurant_order` ADD COLUMN `operator_name` varchar(100) NULL COMMENT ''下单操作人名称快照'' AFTER `operator_id`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_dish_change_log' AND COLUMN_NAME = 'dish_name') = 0,
  'ALTER TABLE `order_dish_change_log` ADD COLUMN `dish_name` varchar(100) NULL COMMENT ''菜品名称快照'' AFTER `dish_id`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_dish_change_log' AND COLUMN_NAME = 'operator_id') = 0,
  'ALTER TABLE `order_dish_change_log` ADD COLUMN `operator_id` bigint NULL COMMENT ''加退菜操作人ID'' AFTER `reason`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_dish_change_log' AND COLUMN_NAME = 'operator_name') = 0,
  'ALTER TABLE `order_dish_change_log` ADD COLUMN `operator_name` varchar(100) NULL COMMENT ''加退菜操作人名称快照'' AFTER `operator_id`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund' AND COLUMN_NAME = 'operator_id') = 0,
  'ALTER TABLE `order_refund` ADD COLUMN `operator_id` bigint NULL COMMENT ''发起退菜操作人ID'' AFTER `reason`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund' AND COLUMN_NAME = 'operator_name') = 0,
  'ALTER TABLE `order_refund` ADD COLUMN `operator_name` varchar(100) NULL COMMENT ''发起退菜操作人名称快照'' AFTER `operator_id`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
