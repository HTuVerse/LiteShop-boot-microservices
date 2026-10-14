USE `liteshop_restaurant`;

-- 已部署定价中心的数据库执行本脚本，使活动可直接绑定门店分组。
SET @schema_name = DATABASE();
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema=@schema_name AND table_name='pricing_campaign_scope'
     AND column_name='store_group_id') = 0,
  'ALTER TABLE `pricing_campaign_scope` ADD COLUMN `store_group_id` bigint NULL COMMENT ''适用门店分组ID；与门店ID互斥'' AFTER `store_id`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema=@schema_name AND table_name='pricing_campaign_scope'
     AND index_name='idx_scope_group') = 0,
  'ALTER TABLE `pricing_campaign_scope` ADD KEY `idx_scope_group` (`tenant_id`,`store_group_id`,`target_type`,`target_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @schema_name = NULL;
SET @ddl = NULL;
