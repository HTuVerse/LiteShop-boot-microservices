-- ============================================================
-- 连锁餐饮菜单第二阶段：门店分组、审批、定时生效与审计
-- 前置条件：已执行 30_chain_menu_phase1.sql。
-- ============================================================
USE `liteshop_restaurant`;

-- 名称优化：明确该成本字段是覆盖值；元数据判断保证脚本可安全重跑。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_dish' AND COLUMN_NAME = 'cost_price') > 0
  AND
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_dish' AND COLUMN_NAME = 'cost_price_override') = 0,
  'ALTER TABLE `store_dish` CHANGE COLUMN `cost_price` `cost_price_override` decimal(10,2) NULL COMMENT ''门店成本覆盖；为空表示继承''',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_dish' AND COLUMN_NAME = 'config_version') = 0,
  'ALTER TABLE `store_dish` ADD COLUMN `config_version` int NOT NULL DEFAULT 1 COMMENT ''门店配置版本'' AFTER `inherited_version`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `store_group` (
  `id` bigint NOT NULL COMMENT '门店分组ID（雪花ID）',
  `group_code` varchar(50) NOT NULL COMMENT '租户内唯一分组编码',
  `group_name` varchar(100) NOT NULL COMMENT '分组名称',
  `description` varchar(300) NULL COMMENT '分组说明',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_group_code` (`tenant_id`, `group_code`),
  KEY `idx_tenant_group_status` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB COMMENT='门店分组';

CREATE TABLE IF NOT EXISTS `store_group_member` (
  `id` bigint NOT NULL COMMENT '分组成员ID（雪花ID）',
  `group_id` bigint NOT NULL COMMENT '门店分组ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_group_store` (`tenant_id`, `group_id`, `store_id`),
  KEY `idx_tenant_store_group` (`tenant_id`, `store_id`, `group_id`)
) ENGINE=InnoDB COMMENT='门店分组成员';

CREATE TABLE IF NOT EXISTS `menu_change_record` (
  `id` bigint NOT NULL COMMENT '菜单变更记录ID（雪花ID）',
  `request_no` varchar(40) NOT NULL COMMENT '变更单号',
  `store_id` bigint NOT NULL COMMENT '目标门店ID',
  `dish_id` bigint NOT NULL COMMENT '目标菜品ID',
  `change_type` tinyint NOT NULL COMMENT '变更类型：0门店菜品配置',
  `before_snapshot` json NOT NULL COMMENT '变更前快照JSON',
  `after_snapshot` json NOT NULL COMMENT '期望变更内容JSON',
  `status` tinyint NOT NULL COMMENT '0待审批，1已审批，2驳回，3撤销，4待生效，5已生效，6执行失败',
  `change_reason` varchar(300) NULL COMMENT '变更原因',
  `effective_at` datetime NULL COMMENT '计划生效时间；为空表示立即生效',
  `requested_by` bigint NULL COMMENT '发起人用户ID',
  `reviewed_by` bigint NULL COMMENT '审批人用户ID',
  `review_remark` varchar(300) NULL COMMENT '审批备注',
  `reviewed_at` datetime NULL COMMENT '审批时间',
  `applied_at` datetime NULL COMMENT '实际生效时间',
  `failure_reason` varchar(500) NULL COMMENT '执行失败原因',
  `version` int NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_request_no` (`tenant_id`, `request_no`),
  KEY `idx_tenant_change_status` (`tenant_id`, `status`, `effective_at`),
  KEY `idx_tenant_store_dish_change` (`tenant_id`, `store_id`, `dish_id`, `created_at`)
) ENGINE=InnoDB COMMENT='菜单变更审批与审计记录';
