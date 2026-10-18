-- 资源引用升级；执行前备份 liteshop_file、liteshop_config、liteshop_restaurant、liteshop_user。
-- 本迁移不修改业务 URL，也不迁移或删除存储对象；可重复执行。
USE `liteshop_file`;

CREATE TABLE IF NOT EXISTS `file_resource_reference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '引用记录ID',
  `resource_id` bigint NOT NULL COMMENT 'file_upload资源ID',
  `tenant_id` bigint NOT NULL COMMENT '引用业务所属租户ID，平台为0',
  `business_type` varchar(40) NOT NULL COMMENT '业务类型，如BANNER、DISH、STORE',
  `business_id` bigint NOT NULL COMMENT '业务记录ID',
  `field_name` varchar(128) NOT NULL COMMENT '业务图片字段名',
  `sort_order` int unsigned NOT NULL DEFAULT 0 COMMENT '图片位置，从0开始；单图为0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '建立时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_resource_slot` (`tenant_id`, `business_type`, `business_id`, `field_name`, `sort_order`, `resource_id`),
  KEY `idx_resource_id` (`resource_id`),
  CONSTRAINT `fk_file_resource_reference_file` FOREIGN KEY (`resource_id`) REFERENCES `file_upload` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源与业务的引用关系';

CREATE TABLE IF NOT EXISTS `file_resource_reference_control` (
  `id` tinyint NOT NULL COMMENT '固定为1的控制记录ID',
  `deletion_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '物理删除开关：0回填待验证，1允许删除',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源引用迁移保护开关';
INSERT INTO `file_resource_reference_control` (`id`, `deletion_enabled`)
VALUES (1, 0) ON DUPLICATE KEY UPDATE `id` = `id`;

-- MySQL 8 不保证 ADD COLUMN IF NOT EXISTS；先查元数据再生成 DDL。
SET @resource_state_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'liteshop_file' AND TABLE_NAME = 'file_upload' AND COLUMN_NAME = 'deletion_state'
);
SET @resource_state_ddl := IF(@resource_state_exists = 0,
  'ALTER TABLE `liteshop_file`.`file_upload` ADD COLUMN `deletion_state` varchar(16) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''资源操作状态：ACTIVE正常、DELETING删除中、UPDATING权限变更中、DELETE_FAILED或CHANGE_FAILED待恢复''',
  'SELECT 1');
PREPARE resource_state_stmt FROM @resource_state_ddl;
EXECUTE resource_state_stmt;
DEALLOCATE PREPARE resource_state_stmt;

-- 历史 URL 精确匹配与对象 Key 定位使用索引；重跑时跳过已有索引。
SET @resource_key_index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'liteshop_file' AND TABLE_NAME = 'file_upload' AND INDEX_NAME = 'idx_file_storage_key'
);
SET @resource_key_ddl := IF(@resource_key_index_exists = 0,
  'CREATE INDEX `idx_file_storage_key` ON `liteshop_file`.`file_upload` (`storage_key`)', 'SELECT 1');
PREPARE resource_key_stmt FROM @resource_key_ddl;
EXECUTE resource_key_stmt;
DEALLOCATE PREPARE resource_key_stmt;

SET @resource_url_index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'liteshop_file' AND TABLE_NAME = 'file_upload' AND INDEX_NAME = 'idx_file_legacy_url'
);
SET @resource_url_ddl := IF(@resource_url_index_exists = 0,
  'CREATE INDEX `idx_file_legacy_url` ON `liteshop_file`.`file_upload` (`url`(191))', 'SELECT 1');
PREPARE resource_url_stmt FROM @resource_url_ddl;
EXECUTE resource_url_stmt;
DEALLOCATE PREPARE resource_url_stmt;

-- 门店多图原为 varchar(255)，不足以保存多个长期公开 URL；仅扩容既有兼容字段。
ALTER TABLE `liteshop_restaurant`.`store_info`
  MODIFY COLUMN `store_images` text NULL COMMENT '门店图片 URL 列表，兼容旧客户端';

-- 校验：不存在悬空引用；同一业务位置的多行仅允许在更新准备阶段短暂出现。
SELECT COUNT(*) AS orphan_references FROM `file_resource_reference` r
LEFT JOIN `file_upload` f ON f.id = r.resource_id WHERE f.id IS NULL;
SELECT tenant_id, business_type, business_id, field_name, sort_order, COUNT(*) AS candidates
FROM `file_resource_reference`
GROUP BY tenant_id, business_type, business_id, field_name, sort_order HAVING COUNT(*) > 1;
-- 备份、回填、对账均完成后才由运维单独执行：
-- UPDATE `file_resource_reference_control` SET `deletion_enabled` = 1 WHERE `id` = 1;
