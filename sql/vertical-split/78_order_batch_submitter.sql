USE `liteshop_restaurant`;

-- 兼容最新建表脚本及重复执行，逐列补齐批次提交人快照。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'restaurant_order_item' AND COLUMN_NAME = 'submitted_by_user_id') = 0,
  'ALTER TABLE `restaurant_order_item` ADD COLUMN `submitted_by_user_id` bigint NULL COMMENT ''本批确认提交订单的用户ID'' AFTER `batch_no`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'restaurant_order_item' AND COLUMN_NAME = 'submitted_by_name') = 0,
  'ALTER TABLE `restaurant_order_item` ADD COLUMN `submitted_by_name` varchar(100) NULL COMMENT ''本批确认提交订单的用户名称快照'' AFTER `submitted_by_user_id`', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

-- 历史首次下单可从订单主表操作人快照恢复；历史后续加菜没有可靠的提交人记录，保持为空。
UPDATE `restaurant_order_item` item
JOIN `restaurant_order` orders ON orders.id = item.order_id AND orders.tenant_id = item.tenant_id
SET item.submitted_by_user_id = orders.operator_id,
    item.submitted_by_name = orders.operator_name
WHERE COALESCE(item.batch_no, 1) = 1;
