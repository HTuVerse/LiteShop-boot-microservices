-- 统一定价中心（破坏性升级）
-- 适用 MySQL 5.7 / 8.0。请在 liteshop_restaurant 数据库执行。
-- 本脚本不保留旧 price/original_price API：正常售价统一为 sale_price，活动价格进入规则表。

SET @schema_name = DATABASE();

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='dish' AND column_name='sale_price') = 0
  AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='dish' AND column_name='original_price') = 1,
  'ALTER TABLE `dish` CHANGE COLUMN `original_price` `sale_price` decimal(10,2) NOT NULL COMMENT ''总部正常售价；活动与会员价由定价中心计算''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='dish' AND column_name='price') = 1,
  'ALTER TABLE `dish` DROP COLUMN `price`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='store_dish' AND column_name='sale_price_override') = 0
  AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='store_dish' AND column_name='original_price_override') = 1,
  'ALTER TABLE `store_dish` CHANGE COLUMN `original_price_override` `sale_price_override` decimal(10,2) NULL COMMENT ''门店正常售价覆盖；为空表示继承总部售价''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='store_dish' AND column_name='price_override') = 1,
  'ALTER TABLE `store_dish` DROP COLUMN `price_override`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='restaurant_order_item' AND column_name='unit_selling_price') = 0
  AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='restaurant_order_item' AND column_name='price') = 1,
  'ALTER TABLE `restaurant_order_item` CHANGE COLUMN `price` `unit_selling_price` decimal(10,2) NOT NULL COMMENT ''会员/活动后的成交单价''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 不保留 category_id 的历史字符串类型；执行前需确认存量值均为数字或 NULL。
ALTER TABLE `restaurant_order_item`
  MODIFY COLUMN `category_id` bigint NULL COMMENT '下单时菜品分类ID';

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='restaurant_order_item' AND column_name='unit_list_price') = 0,
  'ALTER TABLE `restaurant_order_item` ADD COLUMN `unit_list_price` decimal(10,2) NULL COMMENT ''门店正常售价快照（含规格）'' AFTER `category_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE `restaurant_order_item` SET `unit_list_price`=`unit_selling_price` WHERE `unit_list_price` IS NULL;
ALTER TABLE `restaurant_order_item` MODIFY COLUMN `unit_list_price` decimal(10,2) NOT NULL COMMENT '门店正常售价快照（含规格）';

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='restaurant_order_item' AND column_name='discount_price') = 1,
  'ALTER TABLE `restaurant_order_item` CHANGE COLUMN `discount_price` `discount_amount` decimal(10,2) NOT NULL DEFAULT 0 COMMENT ''订单项全部优惠金额''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='restaurant_order_item' AND column_name='total_price') = 1,
  'ALTER TABLE `restaurant_order_item` CHANGE COLUMN `total_price` `line_payable_amount` decimal(10,2) NOT NULL COMMENT ''订单项最终应付金额''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `pricing_campaign` (
  `id` bigint NOT NULL COMMENT '活动ID（雪花ID）',
  `campaign_code` varchar(50) NOT NULL COMMENT '租户内唯一活动编码',
  `campaign_name` varchar(100) NOT NULL COMMENT '活动名称',
  `campaign_type` tinyint NOT NULL COMMENT '活动类型：0直接促销，1数量优惠，2订单优惠',
  `start_time` datetime NULL COMMENT '开始时间；为空立即开始',
  `end_time` datetime NULL COMMENT '结束时间；为空长期有效',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
  `exclusive_group` varchar(50) NOT NULL DEFAULT 'DIRECT_PRICE' COMMENT '互斥分组编码',
  `stack_mode` tinyint NOT NULL DEFAULT 0 COMMENT '叠加方式：0互斥择优，1允许叠加',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
  `version` int NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `created_by` bigint NULL COMMENT '创建人用户ID',
  `updated_by` bigint NULL COMMENT '最后更新人用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_campaign_code` (`tenant_id`,`campaign_code`),
  KEY `idx_campaign_active` (`tenant_id`,`status`,`deleted`,`start_time`,`end_time`)
) ENGINE=InnoDB COMMENT='统一定价活动';

CREATE TABLE IF NOT EXISTS `pricing_rule` (
  `id` bigint NOT NULL COMMENT '定价规则ID（雪花ID）',
  `campaign_id` bigint NOT NULL COMMENT '定价活动ID',
  `condition_type` tinyint NOT NULL COMMENT '条件类型：0始终生效，1数量条件，2订单金额条件',
  `minimum_quantity` int NULL COMMENT '每组最低商品件数',
  `minimum_amount` decimal(12,2) NULL COMMENT '订单金额门槛，单位：元',
  `nth_quantity` int NULL COMMENT '每组内享受优惠的第几件',
  `grouping_type` tinyint NULL COMMENT '分组类型：0同菜品，1分类，2任意商品',
  `action_type` tinyint NOT NULL COMMENT '优惠动作：0固定价，1折扣率，2直减，3赠送',
  `action_value` decimal(12,4) NULL COMMENT '固定价、折扣率或直减金额',
  `repeat_mode` tinyint NOT NULL DEFAULT 0 COMMENT '重复方式：0仅一次，1每组重复',
  `tenant_id` bigint NOT NULL COMMENT '租户ID', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_campaign_rule` (`tenant_id`,`campaign_id`)
) ENGINE=InnoDB COMMENT='定价执行规则';

CREATE TABLE IF NOT EXISTS `pricing_campaign_scope` (
  `id` bigint NOT NULL COMMENT '活动范围ID（雪花ID）',
  `campaign_id` bigint NOT NULL COMMENT '定价活动ID',
  `store_id` bigint NULL COMMENT '适用门店ID；为空表示全部门店',
  `store_group_id` bigint NULL COMMENT '适用门店分组ID；与门店ID互斥',
  `target_type` tinyint NOT NULL COMMENT '目标类型：0全部，1菜品，2分类',
  `target_id` bigint NULL COMMENT '目标菜品或分类ID；全部范围时为空',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`), KEY `idx_scope_match` (`tenant_id`,`store_id`,`store_group_id`,`target_type`,`target_id`),
  KEY `idx_scope_campaign` (`tenant_id`,`campaign_id`)
) ENGINE=InnoDB COMMENT='定价活动范围';

CREATE TABLE IF NOT EXISTS `pricing_campaign_audience` (
  `id` bigint NOT NULL COMMENT '活动人群ID（雪花ID）',
  `campaign_id` bigint NOT NULL COMMENT '定价活动ID',
  `audience_type` tinyint NOT NULL COMMENT '人群类型：0全部，1会员，2指定会员等级',
  `audience_value` int NULL COMMENT '指定会员等级时保存等级编码',
  `tenant_id` bigint NOT NULL COMMENT '租户ID', PRIMARY KEY (`id`),
  KEY `idx_audience_campaign` (`tenant_id`,`campaign_id`),
  KEY `idx_audience_match` (`tenant_id`,`audience_type`,`audience_value`)
) ENGINE=InnoDB COMMENT='定价活动适用人群';

CREATE TABLE IF NOT EXISTS `member_dish_price` (
  `id` bigint NOT NULL COMMENT '会员菜品价ID（雪花ID）',
  `store_id` bigint NULL COMMENT '适用门店ID；为空表示全部门店',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `member_level_code` int NOT NULL COMMENT '会员等级编码，对应sys_user.level',
  `member_price` decimal(10,2) NOT NULL COMMENT '常态会员售价，单位：元',
  `start_time` datetime NULL COMMENT '开始时间；为空立即生效',
  `end_time` datetime NULL COMMENT '结束时间；为空长期有效',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID', PRIMARY KEY (`id`),
  KEY `idx_member_price_match` (`tenant_id`,`dish_id`,`member_level_code`,`store_id`,`status`,`deleted`)
) ENGINE=InnoDB COMMENT='常态会员等级菜品价';

CREATE TABLE IF NOT EXISTS `order_price_adjustment` (
  `id` bigint NOT NULL COMMENT '价格调整ID（雪花ID）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint NOT NULL COMMENT '订单明细ID',
  `campaign_id` bigint NULL COMMENT '定价活动ID；会员价或人工调整时为空',
  `adjustment_type` tinyint NOT NULL COMMENT '调整类型：0会员价，1直接促销，2第N件优惠，3订单优惠，4赠品，5人工折扣',
  `before_amount` decimal(12,2) NOT NULL COMMENT '调整前金额，单位：元',
  `adjustment_amount` decimal(12,2) NOT NULL COMMENT '减免金额，正数，单位：元',
  `after_amount` decimal(12,2) NOT NULL COMMENT '调整后金额，单位：元',
  `rule_snapshot` json NULL COMMENT '下单时规则不可变快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`), KEY `idx_adjustment_order` (`tenant_id`,`order_id`),
  KEY `idx_adjustment_item` (`tenant_id`,`order_item_id`),
  KEY `idx_adjustment_campaign` (`tenant_id`,`campaign_id`)
) ENGINE=InnoDB COMMENT='订单价格调整快照';

SET @schema_name = NULL;
SET @ddl = NULL;
