-- ============================================================
-- 连锁餐饮菜单第一阶段：总部菜品、门店自建、门店改价与上下架
-- 执行前请备份；本脚本仅做增量扩展，不删除旧字段和历史数据。
-- ============================================================
USE `liteshop_restaurant`;

ALTER TABLE `dish`
  MODIFY COLUMN `store_id` bigint NULL COMMENT '迁移兼容字段；门店自建菜品所属门店，总部菜品为空';

-- MySQL 不支持 MariaDB 风格的 ADD COLUMN IF NOT EXISTS，改为元数据判断后执行。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'dish_code') = 0,
  'ALTER TABLE `dish` ADD COLUMN `dish_code` varchar(50) NULL COMMENT ''租户内稳定菜品编码'' AFTER `id`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'source_type') = 0,
  'ALTER TABLE `dish` ADD COLUMN `source_type` tinyint NOT NULL DEFAULT 1 COMMENT ''来源：0总部，1门店'' AFTER `store_id`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'owner_store_id') = 0,
  'ALTER TABLE `dish` ADD COLUMN `owner_store_id` bigint NULL COMMENT ''门店自建菜品所属门店；总部菜品为空'' AFTER `source_type`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'master_status') = 0,
  'ALTER TABLE `dish` ADD COLUMN `master_status` tinyint NOT NULL DEFAULT 1 COMMENT ''总部状态：0草稿，1启用，2全局停用'' AFTER `owner_store_id`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'version') = 0,
  'ALTER TABLE `dish` ADD COLUMN `version` int NOT NULL DEFAULT 1 COMMENT ''菜品主数据版本'' AFTER `master_status`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `dish` ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0正常，1删除'' AFTER `version`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `dish`
SET `dish_code` = CONCAT('D', `id`),
    `source_type` = 1,
    `owner_store_id` = `store_id`,
    `master_status` = 1,
    `version` = 1,
    `deleted` = 0
WHERE `dish_code` IS NULL OR `dish_code` = '';

ALTER TABLE `dish`
  MODIFY COLUMN `dish_code` varchar(50) NOT NULL COMMENT '租户内稳定菜品编码';

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND INDEX_NAME = 'uk_tenant_dish_code') = 0,
  'ALTER TABLE `dish` ADD UNIQUE INDEX `uk_tenant_dish_code` (`tenant_id`, `dish_code`)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND INDEX_NAME = 'idx_tenant_source_owner') = 0,
  'ALTER TABLE `dish` ADD INDEX `idx_tenant_source_owner` (`tenant_id`, `source_type`, `owner_store_id`, `deleted`)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND INDEX_NAME = 'idx_tenant_master_status') = 0,
  'ALTER TABLE `dish` ADD INDEX `idx_tenant_master_status` (`tenant_id`, `master_status`, `deleted`)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_category' AND COLUMN_NAME = 'source_type') = 0,
  'ALTER TABLE `dish_category` ADD COLUMN `source_type` tinyint NOT NULL DEFAULT 1 COMMENT ''来源：0总部，1门店'' AFTER `store_id`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_category' AND COLUMN_NAME = 'owner_store_id') = 0,
  'ALTER TABLE `dish_category` ADD COLUMN `owner_store_id` bigint NULL COMMENT ''门店自建分类所属门店；总部分类为空'' AFTER `source_type`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_category' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `dish_category` ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0正常，1删除'' AFTER `owner_store_id`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_category' AND INDEX_NAME = 'idx_tenant_category_scope') = 0,
  'ALTER TABLE `dish_category` ADD INDEX `idx_tenant_category_scope` (`tenant_id`, `source_type`, `owner_store_id`, `deleted`)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `dish_category`
SET `source_type` = CASE WHEN `store_id` IS NULL THEN 0 ELSE 1 END,
    `owner_store_id` = `store_id`
-- 新增列的默认组合是 source_type=1、owner_store_id=NULL；只回填该组合，避免重跑时改写新数据。
WHERE `source_type` = 1 AND `owner_store_id` IS NULL;

CREATE TABLE IF NOT EXISTS `store_dish` (
  `id` bigint NOT NULL COMMENT '门店菜品配置ID（雪花ID）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `dish_id` bigint NOT NULL COMMENT '菜品主数据ID',
  `category_override_id` bigint NULL COMMENT '门店分类覆盖；为空表示继承',
  `sale_price_override` decimal(10,2) NULL COMMENT '门店正常售价覆盖；为空表示继承',
  `cost_price` decimal(10,2) NULL COMMENT '门店实际成本；为空表示继承',
  `listing_status` tinyint NOT NULL DEFAULT 1 COMMENT '0下架，1上架，2清退',
  `sold_out` tinyint NOT NULL DEFAULT 0 COMMENT '临时售罄：0否，1是',
  `inventory_mode` tinyint NOT NULL DEFAULT 1 COMMENT '库存模式：0不限库存，1跟踪库存',
  `stock` int NOT NULL DEFAULT 0 COMMENT '门店库存',
  `recommend_override` tinyint NULL COMMENT '推荐覆盖；为空表示继承',
  `hot_override` tinyint NULL COMMENT '热门覆盖；为空表示继承',
  `new_override` tinyint NULL COMMENT '新品覆盖；为空表示继承',
  `sort_order_override` int NULL COMMENT '排序覆盖；为空表示继承',
  `sales_count` int NOT NULL DEFAULT 0 COMMENT '门店累计销量',
  `inherited_version` int NOT NULL DEFAULT 1 COMMENT '已继承的总部菜品版本',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_store_dish` (`tenant_id`, `store_id`, `dish_id`),
  KEY `idx_store_listing` (`tenant_id`, `store_id`, `listing_status`, `sold_out`, `deleted`),
  KEY `idx_tenant_dish` (`tenant_id`, `dish_id`)
) ENGINE=InnoDB COMMENT='门店菜品个性化配置';

-- 存量菜品原本一菜一店，使用原菜品ID作为首次配置ID可安全回填。
INSERT IGNORE INTO `store_dish`
  (`id`, `store_id`, `dish_id`, `listing_status`, `sold_out`, `inventory_mode`, `stock`,
   `sales_count`, `inherited_version`, `deleted`, `created_at`, `updated_at`, `tenant_id`)
SELECT `id`, `store_id`, `id`, `sale_status`, 0, 1, `stock`, `sales_count`, 1, 0,
       `created_at`, `updated_at`, `tenant_id`
FROM `dish`
WHERE `store_id` IS NOT NULL;

CREATE TABLE IF NOT EXISTS `store_menu_policy` (
  `id` bigint NOT NULL COMMENT '策略ID（雪花ID）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `allow_local_dish` tinyint NOT NULL DEFAULT 1 COMMENT '允许门店自建菜品',
  `allow_local_category` tinyint NOT NULL DEFAULT 1 COMMENT '允许门店自建分类',
  `allow_category_override` tinyint NOT NULL DEFAULT 1 COMMENT '允许门店调整菜品分类',
  `allow_price_override` tinyint NOT NULL DEFAULT 1 COMMENT '允许门店覆盖价格',
  `allow_store_off_shelf` tinyint NOT NULL DEFAULT 1 COMMENT '允许门店自主下架',
  `allow_store_exit` tinyint NOT NULL DEFAULT 1 COMMENT '允许门店清退总部菜品',
  `min_price_rate` decimal(6,4) NOT NULL DEFAULT 0.0000 COMMENT '门店最低售价比例',
  `max_price_rate` decimal(6,4) NOT NULL DEFAULT 10.0000 COMMENT '门店最高售价比例',
  `require_approval` tinyint NOT NULL DEFAULT 0 COMMENT '是否需要审批；第一阶段预留',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_store_policy` (`tenant_id`, `store_id`)
) ENGINE=InnoDB COMMENT='门店菜单策略';
