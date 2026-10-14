-- ============================================================
-- 门店菜单策略扩展：自建分类、调整菜品分类
-- 前置条件：已执行 30_chain_menu_phase1.sql。
-- 兼容 MySQL 5.7/8.0，可安全重复执行。
-- ============================================================
USE `liteshop_restaurant`;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'store_menu_policy'
     AND COLUMN_NAME = 'allow_local_category') = 0,
  'ALTER TABLE `store_menu_policy` ADD COLUMN `allow_local_category` tinyint NOT NULL DEFAULT 1 COMMENT ''允许门店自建分类'' AFTER `allow_local_dish`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'store_menu_policy'
     AND COLUMN_NAME = 'allow_category_override') = 0,
  'ALTER TABLE `store_menu_policy` ADD COLUMN `allow_category_override` tinyint NOT NULL DEFAULT 1 COMMENT ''允许门店调整菜品分类'' AFTER `allow_local_category`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
