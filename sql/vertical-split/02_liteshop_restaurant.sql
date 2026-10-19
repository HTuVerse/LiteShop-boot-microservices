-- ============================================================
-- liteshop_restaurant  —— 餐饮服务库（liteShop-restaurant-service）
-- 变更说明（相对 hongtu 单库）：
--   1. 主键改雪花 ID：bigint，去掉 AUTO_INCREMENT
--   2. 移除全部外键
--   3. 全部业务表末尾追加 tenant_id（多租户预留）
--   4. 统一排序规则 utf8mb4_0900_ai_ci（原库 unicode_ci / 0900_ai_ci 混用）
--   5. 索引优化（重点）：
--      - dish：删除 8 个低选择性/冗余单列索引（sale_status、scene_type、sort_order、
--        recommend/hot/new、tags、created_at 等），保留复合索引
--      - restaurant_order：保留核心业务索引，并为支付时间、取消时间统计建立租户前缀索引
--      - table_session：删除误建在 current_user_count 上的 idx_user_id
--   6. store_code 唯一键改为租户内唯一
-- ============================================================

CREATE DATABASE IF NOT EXISTS `liteshop_restaurant` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `liteshop_restaurant`;

-- ----------------------------
-- 门店信息表
-- ----------------------------
DROP TABLE IF EXISTS `store_info`;
CREATE TABLE `store_info` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `store_name` varchar(100) NOT NULL COMMENT '门店名称',
  `store_code` varchar(50) NOT NULL COMMENT '门店编码',
  `address` varchar(255) NULL DEFAULT NULL COMMENT '门店地址',
  `phone` varchar(20) NULL DEFAULT NULL COMMENT '联系电话',
  `business_hours` varchar(100) NULL DEFAULT NULL COMMENT '营业时间',
  `payment_mode` tinyint NOT NULL DEFAULT 1 COMMENT '支付模式：1-线上支付，2-线下支付',
  `pickup_mode` tinyint NULL DEFAULT NULL COMMENT '取餐模式：0-送上桌，1-叫号自取',
  `is_print` tinyint NULL DEFAULT NULL COMMENT '是否开启小票打印：1:是，0:否',
  `allow_pre_order` tinyint NOT NULL DEFAULT 1 COMMENT '是否允许预点单：0-否，1-是',
  `pre_order_limit` int NULL DEFAULT 10 COMMENT '预点单时间限制（分钟）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-停业，1-营业，2-休息中',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '门店简介',
  `store_images` varchar(255) NULL DEFAULT NULL COMMENT '门店图片',
  `longitude` double NULL DEFAULT NULL COMMENT '经度（用于地图定位）',
  `latitude` double NULL DEFAULT NULL COMMENT '纬度（用于地图定位）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_store_code`(`tenant_id`, `store_code`),
  INDEX `idx_tenant_id`(`tenant_id`)
) ENGINE = InnoDB COMMENT = '门店信息表';

-- ----------------------------
-- 菜品分类表
-- ----------------------------
DROP TABLE IF EXISTS `dish_category`;
CREATE TABLE `dish_category` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店ID',
  `source_type` tinyint NOT NULL DEFAULT 1 COMMENT '来源：0总部，1门店',
  `owner_store_id` bigint NULL COMMENT '门店自建分类所属门店；总部分类为空',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `category_name` varchar(50) NOT NULL COMMENT '分类名称',
  `category_desc` varchar(255) NULL DEFAULT NULL COMMENT '分类描述',
  `category_pic` varchar(255) NULL DEFAULT NULL COMMENT '分类图片',
  `category_icon` varchar(255) NULL DEFAULT NULL COMMENT '分类icon',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '分类层级：1-一级，2-二级',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，数字越小越靠前',
  `is_show` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示：0-隐藏，1-显示',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_store_id`(`store_id`),
  INDEX `idx_parent_id`(`parent_id`),
  INDEX `idx_tenant_category_scope`(`tenant_id`, `source_type`, `owner_store_id`, `deleted`)
) ENGINE = InnoDB COMMENT = '菜品分类表';

-- ----------------------------
-- 菜品表
-- ----------------------------
DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `dish_code` varchar(50) NULL COMMENT '租户内稳定菜品编码；应用写入时必填',
  `store_id` bigint NULL COMMENT '迁移兼容字段；门店自建菜品所属门店，总部菜品为空',
  `source_type` tinyint NOT NULL DEFAULT 1 COMMENT '来源：0总部，1门店',
  `owner_store_id` bigint NULL COMMENT '门店自建菜品所属门店；总部菜品为空',
  `master_status` tinyint NOT NULL DEFAULT 1 COMMENT '总部状态：0草稿，1启用，2全局停用',
  `version` int NOT NULL DEFAULT 1 COMMENT '菜品主数据版本',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `dish_name` varchar(100) NOT NULL COMMENT '菜品名称',
  `dish_desc` text NULL COMMENT '菜品描述',
  `dish_pics` text NULL COMMENT '菜品图片（JSON数组）',
  `sale_price` decimal(10, 2) NOT NULL COMMENT '总部正常售价；活动与会员价由定价中心计算',
  `cost_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '成本价',
  `unit` varchar(10) NULL DEFAULT '份' COMMENT '单位',
  `stock` int NOT NULL DEFAULT 999 COMMENT '库存数量',
  `sales_count` int NOT NULL DEFAULT 0 COMMENT '销量',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览量',
  `is_recommend` tinyint NOT NULL DEFAULT 0 COMMENT '是否推荐：0-否，1-是',
  `is_hot` tinyint NOT NULL DEFAULT 0 COMMENT '是否热门：0-否，1-是',
  `is_new` tinyint NOT NULL DEFAULT 0 COMMENT '是否新品：0-否，1-是',
  `scene_type` tinyint NOT NULL DEFAULT 3 COMMENT '适用场景：0-堂食，1-外卖，2-通用',
  `sale_status` tinyint NOT NULL DEFAULT 1 COMMENT '销售状态：0-下架，1-上架',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，数字越小越靠前',
  `tags` varchar(255) NULL DEFAULT NULL COMMENT '商品标签（JSON数组）',
  `allergens` varchar(255) NULL DEFAULT NULL COMMENT '过敏原信息',
  `nutrition_info` text NULL COMMENT '营养信息（JSON）',
  `cooking_time` int NULL DEFAULT 0 COMMENT '制作时间（分钟）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_dish_code`(`tenant_id`, `dish_code`),
  INDEX `idx_store_category_status`(`store_id`, `category_id`, `sale_status`),
  INDEX `idx_store_sale_status`(`store_id`, `sale_status`, `sort_order`),
  INDEX `idx_category_id`(`category_id`),
  INDEX `idx_tenant_source_owner`(`tenant_id`, `source_type`, `owner_store_id`, `deleted`),
  INDEX `idx_tenant_master_status`(`tenant_id`, `master_status`, `deleted`)
) ENGINE = InnoDB COMMENT = '菜品表';

-- ----------------------------
-- 门店菜品个性化配置
-- ----------------------------
DROP TABLE IF EXISTS `store_dish`;
CREATE TABLE `store_dish` (
  `id` bigint NOT NULL COMMENT '门店菜品配置ID（雪花ID）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `dish_id` bigint NOT NULL COMMENT '菜品主数据ID',
  `category_override_id` bigint NULL COMMENT '门店分类覆盖；为空表示继承',
  `sale_price_override` decimal(10,2) NULL COMMENT '门店正常售价覆盖；为空表示继承总部售价',
  `cost_price_override` decimal(10,2) NULL COMMENT '门店成本覆盖；为空表示继承',
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
  `config_version` int NOT NULL DEFAULT 1 COMMENT '门店配置版本',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_store_dish`(`tenant_id`, `store_id`, `dish_id`),
  INDEX `idx_store_listing`(`tenant_id`, `store_id`, `listing_status`, `sold_out`, `deleted`),
  INDEX `idx_tenant_dish`(`tenant_id`, `dish_id`)
) ENGINE = InnoDB COMMENT = '门店菜品个性化配置';

-- ----------------------------
-- 门店菜单策略
-- ----------------------------
DROP TABLE IF EXISTS `store_menu_policy`;
CREATE TABLE `store_menu_policy` (
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
  `require_approval` tinyint NOT NULL DEFAULT 0 COMMENT '门店菜品变更是否需要总部审批',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_store_policy`(`tenant_id`, `store_id`)
) ENGINE = InnoDB COMMENT = '门店菜单策略';

-- ----------------------------
-- 统一定价中心
-- ----------------------------
DROP TABLE IF EXISTS `order_price_adjustment`;
DROP TABLE IF EXISTS `pricing_campaign_audience`;
DROP TABLE IF EXISTS `pricing_campaign_scope`;
DROP TABLE IF EXISTS `pricing_rule`;
DROP TABLE IF EXISTS `member_dish_price`;
DROP TABLE IF EXISTS `pricing_campaign`;

CREATE TABLE `pricing_campaign` (
  `id` bigint NOT NULL COMMENT '活动ID',
  `campaign_code` varchar(50) NOT NULL COMMENT '租户内唯一活动编码',
  `campaign_name` varchar(100) NOT NULL COMMENT '活动名称',
  `campaign_type` tinyint NOT NULL COMMENT '活动类型：0直接促销，1数量优惠，2订单优惠',
  `start_time` datetime NULL COMMENT '开始时间；为空立即开始',
  `end_time` datetime NULL COMMENT '结束时间；为空长期有效',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
  `exclusive_group` varchar(50) NOT NULL DEFAULT 'DIRECT_PRICE' COMMENT '互斥组',
  `stack_mode` tinyint NOT NULL DEFAULT 0 COMMENT '叠加方式：0互斥择优，1允许叠加',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0停用，1启用',
  `version` int NOT NULL DEFAULT 1 COMMENT '乐观版本',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `created_by` bigint NULL COMMENT '创建人用户ID',
  `updated_by` bigint NULL COMMENT '最后更新人用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_campaign_code` (`tenant_id`,`campaign_code`),
  KEY `idx_campaign_active` (`tenant_id`,`status`,`deleted`,`start_time`,`end_time`)
) ENGINE=InnoDB COMMENT='统一定价活动';

CREATE TABLE `pricing_rule` (
  `id` bigint NOT NULL COMMENT '定价规则ID（雪花ID）',
  `campaign_id` bigint NOT NULL COMMENT '定价活动ID',
  `condition_type` tinyint NOT NULL COMMENT '条件类型：0始终生效，1数量条件，2订单金额条件',
  `minimum_quantity` int NULL COMMENT '每组最低商品件数',
  `minimum_amount` decimal(12,2) NULL COMMENT '订单金额门槛，单位：元',
  `nth_quantity` int NULL COMMENT '优惠第几件',
  `grouping_type` tinyint NULL COMMENT '分组类型：0同菜品，1分类，2任意商品',
  `action_type` tinyint NOT NULL COMMENT '优惠动作：0固定价，1折扣率，2直减，3赠送',
  `action_value` decimal(12,4) NULL COMMENT '固定价、折扣率或直减金额',
  `repeat_mode` tinyint NOT NULL DEFAULT 0 COMMENT '重复方式：0仅一次，1每组重复',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_campaign_rule` (`tenant_id`,`campaign_id`)
) ENGINE=InnoDB COMMENT='定价执行规则';

CREATE TABLE `pricing_campaign_scope` (
  `id` bigint NOT NULL COMMENT '活动范围ID（雪花ID）',
  `campaign_id` bigint NOT NULL COMMENT '定价活动ID',
  `store_id` bigint NULL COMMENT '为空表示全部门店',
  `store_group_id` bigint NULL COMMENT '适用门店分组ID；与门店ID互斥',
  `target_type` tinyint NOT NULL COMMENT '目标类型：0全部，1菜品，2分类',
  `target_id` bigint NULL COMMENT '目标菜品或分类ID；全部范围时为空',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_scope_match` (`tenant_id`,`store_id`,`store_group_id`,`target_type`,`target_id`),
  KEY `idx_scope_campaign` (`tenant_id`,`campaign_id`)
) ENGINE=InnoDB COMMENT='定价活动范围';

CREATE TABLE `pricing_campaign_audience` (
  `id` bigint NOT NULL COMMENT '活动人群ID（雪花ID）',
  `campaign_id` bigint NOT NULL COMMENT '定价活动ID',
  `audience_type` tinyint NOT NULL COMMENT '人群类型：0全部，1会员，2指定会员等级',
  `audience_value` int NULL COMMENT '指定会员等级时保存等级编码',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_audience_campaign` (`tenant_id`,`campaign_id`),
  KEY `idx_audience_match` (`tenant_id`,`audience_type`,`audience_value`)
) ENGINE=InnoDB COMMENT='定价活动适用人群';

CREATE TABLE `member_dish_price` (
  `id` bigint NOT NULL COMMENT '会员菜品价ID（雪花ID）',
  `store_id` bigint NULL COMMENT '为空表示全部门店',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `member_level_code` int NOT NULL COMMENT 'sys_user.level等级编码',
  `member_price` decimal(10,2) NOT NULL COMMENT '常态会员售价',
  `start_time` datetime NULL COMMENT '开始时间；为空立即生效',
  `end_time` datetime NULL COMMENT '结束时间；为空长期有效',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_member_price_match` (`tenant_id`,`dish_id`,`member_level_code`,`store_id`,`status`,`deleted`)
) ENGINE=InnoDB COMMENT='常态会员等级菜品价';

-- ----------------------------
-- 门店分组
-- ----------------------------
DROP TABLE IF EXISTS `store_group_member`;
DROP TABLE IF EXISTS `store_group`;
CREATE TABLE `store_group` (
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
  UNIQUE INDEX `uk_tenant_group_code` (`tenant_id`, `group_code`),
  INDEX `idx_tenant_group_status` (`tenant_id`, `status`, `deleted`)
) ENGINE = InnoDB COMMENT = '门店分组';

CREATE TABLE `store_group_member` (
  `id` bigint NOT NULL COMMENT '分组成员ID（雪花ID）',
  `group_id` bigint NOT NULL COMMENT '门店分组ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_group_store` (`tenant_id`, `group_id`, `store_id`),
  INDEX `idx_tenant_store_group` (`tenant_id`, `store_id`, `group_id`)
) ENGINE = InnoDB COMMENT = '门店分组成员';

-- ----------------------------
-- 菜单变更审批与审计
-- ----------------------------
DROP TABLE IF EXISTS `menu_change_record`;
CREATE TABLE `menu_change_record` (
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
  UNIQUE INDEX `uk_tenant_request_no` (`tenant_id`, `request_no`),
  INDEX `idx_tenant_change_status` (`tenant_id`, `status`, `effective_at`),
  INDEX `idx_tenant_store_dish_change` (`tenant_id`, `store_id`, `dish_id`, `created_at`)
) ENGINE = InnoDB COMMENT = '菜单变更审批与审计记录';

-- ----------------------------
-- 菜品规格选项表
-- ----------------------------
DROP TABLE IF EXISTS `dish_spec_option`;
CREATE TABLE `dish_spec_option` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `spec_name` varchar(50) NOT NULL COMMENT '规格名称（如：分量、做法、温度）',
  `spec_values` text NOT NULL COMMENT '可选值JSON数组',
  `is_required` tinyint NOT NULL DEFAULT 1 COMMENT '是否必选：0-否，1-是',
  `more_select` tinyint NOT NULL DEFAULT 0 COMMENT '是否多选：0-否，1-是',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_dish_status`(`dish_id`, `status`)
) ENGINE = InnoDB COMMENT = '菜品规格选项表';

-- ----------------------------
-- 餐桌信息表
-- ----------------------------
DROP TABLE IF EXISTS `table_info`;
CREATE TABLE `table_info` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_number` varchar(20) NOT NULL COMMENT '餐桌编号',
  `table_name` varchar(50) NULL DEFAULT NULL COMMENT '餐桌名称',
  `h5_url` varchar(600) NULL DEFAULT NULL COMMENT '当前租户H5桌位入口',
  `h5_image` longtext NULL COMMENT 'H5桌位码图片Data URI',
  `wx_image` longtext NULL COMMENT '微信官方小程序码图片Data URI',
  `scan_token_id` varchar(32) NULL COMMENT '扫码票据编号，轮换后旧桌位码失效',
  `wx_source` varchar(200) NULL COMMENT '微信码对应的scene及租户小程序凭据版本',
  `wx_status` varchar(16) NULL COMMENT '微信码生成状态：READY或FAILED',
  `seats` int NOT NULL DEFAULT 4 COMMENT '座位数',
  `location` varchar(100) NULL DEFAULT NULL COMMENT '位置描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-空闲，1-使用中，2-预留，3-停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_store_table`(`store_id`, `table_number`),
  INDEX `idx_tenant_store_status_created` (`tenant_id`, `store_id`, `status`, `created_at`)
) ENGINE = InnoDB COMMENT = '餐桌信息表';

-- ----------------------------
-- 餐桌预约记录表
-- ----------------------------
DROP TABLE IF EXISTS `table_reservation`;
CREATE TABLE `table_reservation` (
  `id` bigint NOT NULL COMMENT '预约ID（雪花ID）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_id` bigint NOT NULL COMMENT '桌位ID',
  `reservation_phone` varchar(20) NOT NULL COMMENT '预约手机号',
  `people_count` int NOT NULL DEFAULT 1 COMMENT '预约人数',
  `expected_arrival_time` datetime NOT NULL COMMENT '预计到店时间',
  `expected_end_time` datetime NOT NULL COMMENT '预计结束时间',
  `reservation_remark` varchar(200) NULL DEFAULT NULL COMMENT '预约备注',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待到店，1-已到店，2-已完成，3-已取消，4-爽约',
  `session_no` varchar(32) NULL DEFAULT NULL COMMENT '关联桌位会话编号',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '创建预约的操作人ID',
  `cancel_reason` varchar(200) NULL DEFAULT NULL COMMENT '取消原因',
  `arrived_at` datetime NULL DEFAULT NULL COMMENT '实际到店时间',
  `completed_at` datetime NULL DEFAULT NULL COMMENT '实际完成时间',
  `cancelled_at` datetime NULL DEFAULT NULL COMMENT '取消时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  INDEX `idx_reservation_table_time` (`tenant_id`, `table_id`, `status`, `expected_arrival_time`, `expected_end_time`),
  INDEX `idx_reservation_store_time` (`tenant_id`, `store_id`, `expected_arrival_time`),
  INDEX `idx_reservation_phone` (`tenant_id`, `reservation_phone`),
  INDEX `idx_reservation_session` (`tenant_id`, `session_no`)
) ENGINE = InnoDB COMMENT = '餐桌预约记录表';

-- ----------------------------
-- 餐桌会话表
-- ----------------------------
DROP TABLE IF EXISTS `table_session`;
CREATE TABLE `table_session` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `session_no` varchar(32) NOT NULL COMMENT '会话编号',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_id` bigint NOT NULL COMMENT '餐桌ID',
  `current_order_id` bigint NULL DEFAULT NULL COMMENT '当前订单ID(会话期间的主订单)',
  `current_user_count` bigint NULL DEFAULT NULL COMMENT '当前在线人数',
  `people_count` bigint NULL DEFAULT NULL COMMENT '就餐人数',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `total_orders` int NULL DEFAULT 0 COMMENT '总订单数',
  `total_amount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '总消费金额',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1-进行中，2-已结束',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `paid_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '实付金额',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_session_no`(`session_no`),
  INDEX `idx_store_table_status`(`store_id`, `table_id`, `status`),
  INDEX `idx_current_order_id`(`current_order_id`),
  INDEX `idx_tenant_table_status_time` (`tenant_id`, `table_id`, `status`, `created_at`),
  INDEX `idx_tenant_store_status_start` (`tenant_id`, `store_id`, `status`, `start_time`)
) ENGINE = InnoDB COMMENT = '餐桌会话表';

-- ----------------------------
-- 餐厅订单表
-- ----------------------------
DROP TABLE IF EXISTS `restaurant_order`;
CREATE TABLE `restaurant_order` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `request_key` varchar(64) NULL DEFAULT NULL COMMENT '租户内下单幂等键（用户下单必填）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_id` bigint NULL DEFAULT NULL COMMENT '餐桌ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID（可选）',
  `operator_id` bigint NULL COMMENT '下单操作人ID，与会员ID分离',
  `operator_name` varchar(100) NULL COMMENT '下单操作人名称快照',
  `session_no` varchar(64) NULL DEFAULT NULL COMMENT '会话编号',
  `order_type` tinyint NOT NULL DEFAULT 1 COMMENT '0-在线点餐 ，1-预点餐，2-门店自提',
  `dining_count` int NULL DEFAULT NULL COMMENT '就餐人数',
  `total_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
  `discount_amount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '优惠金额',
  `discount_rate` decimal(5, 2) NULL DEFAULT 1.00 COMMENT '整单折扣率（例如：0.8表示8折，1.0表示无折扣）',
  `actual_amount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '实际支付金额',
  `payment_mode` tinyint NOT NULL COMMENT '支付模式：1-线上支付，2-线下支付',
  `payment_status` tinyint NOT NULL DEFAULT 0 COMMENT '支付状态：0未支付，1已支付，2部分退款，3全额退款，4支付超时，5取消支付，6支付失败，7对账中，8已收款待处理',
  `payment_type` tinyint NULL DEFAULT NULL COMMENT '支付方式：1-微信，2-支付宝，3-现金，4-银行卡',
  `payment_time` datetime NULL DEFAULT NULL COMMENT '支付时间',
  `payment_no` varchar(64) NULL DEFAULT NULL COMMENT '支付流水号',
  `stock_reserved` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已扣减门店库存：0否，1是',
  `sales_committed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '销量是否已计入：0否，1是',
  `pickup_code` varchar(32) NULL DEFAULT NULL COMMENT '取餐码',
  `pickup_date` date NULL DEFAULT NULL COMMENT '取餐码所属营业日期',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '订单状态：1-已下单，2-已接单，3-制作中，4-制作完成，5-已完成，6-已取消',
  `note` varchar(255) NULL DEFAULT NULL COMMENT '订单备注',
  `cancel_reason` varchar(255) NULL DEFAULT NULL COMMENT '取消原因',
  `cancel_time` datetime NULL DEFAULT NULL COMMENT '取消时间',
  `service_fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '服务费',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dining_end_time` datetime NULL DEFAULT NULL COMMENT '用餐结束时间',
  `user_coupon_ids` varchar(255) NULL DEFAULT NULL COMMENT '对应 user_coupon 表的 id（逗号分隔）',
  `coupon_types` varchar(255) NULL DEFAULT NULL COMMENT '使用的优惠券类型（逗号分隔）',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_order_no`(`order_no`),
  UNIQUE INDEX `uk_tenant_request_key`(`tenant_id`, `request_key`),
  UNIQUE INDEX `uk_tenant_store_pickup`(`tenant_id`, `store_id`, `pickup_date`, `pickup_code`),
  INDEX `idx_store_created_at`(`store_id`, `created_at`),
  INDEX `idx_store_status_time`(`store_id`, `status`, `created_at`),
  INDEX `idx_store_payment_time`(`tenant_id`, `store_id`, `payment_status`, `payment_time`),
  INDEX `idx_tenant_payment_date_store`(`tenant_id`, `payment_time`, `store_id`, `payment_status`),
  INDEX `idx_store_cancel_time`(`tenant_id`, `store_id`, `status`, `cancel_time`),
  INDEX `idx_store_table_status`(`store_id`, `table_id`, `status`),
  INDEX `idx_user_session_status`(`user_id`, `session_no`, `status`),
  INDEX `idx_session_no`(`session_no`),
  INDEX `idx_tenant_session_created` (`tenant_id`, `session_no`, `created_at`),
  INDEX `idx_tenant_created`(`tenant_id`, `created_at`)
) ENGINE = InnoDB COMMENT = '餐厅订单表';

-- ----------------------------
-- 打印任务
-- ----------------------------
DROP TABLE IF EXISTS `print_job`;
CREATE TABLE `print_job` (
  `id` bigint NOT NULL COMMENT '打印任务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '业务订单ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `printer_id` bigint NOT NULL COMMENT '打印机ID',
  `printer_sn` varchar(64) NOT NULL COMMENT '打印机编号快照',
  `provider_order_id` varchar(128) NULL COMMENT '飞鹅云打印订单ID',
  `print_copies` int NOT NULL DEFAULT 1 COMMENT '该任务打印联数，1到5联',
  `status` tinyint NOT NULL COMMENT '0已提交 1成功 2失败 3提交失败 4提交中',
  `attempts` int NOT NULL DEFAULT 1 COMMENT '提交次数',
  `last_error` varchar(500) NULL COMMENT '最后错误',
  `callback_time` datetime NULL COMMENT '最后回调时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_provider_order_id` (`provider_order_id`),
  INDEX `idx_tenant_order` (`tenant_id`, `order_id`),
  INDEX `idx_tenant_status_time` (`tenant_id`, `status`, `created_at`)
) ENGINE = InnoDB COMMENT = '打印任务';

-- ----------------------------
-- 订单商品表
-- ----------------------------
DROP TABLE IF EXISTS `restaurant_order_item`;
CREATE TABLE `restaurant_order_item` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `category_id` bigint NULL COMMENT '下单时菜品分类ID',
  `unit_list_price` decimal(10, 2) NOT NULL COMMENT '门店正常售价快照（含规格）',
  `unit_selling_price` decimal(10, 2) NOT NULL COMMENT '会员/活动后的成交单价',
  `unit_cost_snapshot` decimal(10, 2) NULL COMMENT '下单时门店实际单位成本快照；为空表示未维护成本',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `discount_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '订单项全部优惠金额',
  `line_payable_amount` decimal(10, 2) NOT NULL COMMENT '订单项最终应付金额',
  `line_cost_amount` decimal(12, 2) NULL COMMENT '当前剩余数量对应的成交成本总额',
  `is_gift` tinyint NULL DEFAULT 0 COMMENT '是否赠送（0-否，1-是）',
  `spec_options_text` varchar(255) NULL DEFAULT NULL COMMENT '选择的规格',
  `note` varchar(255) NULL DEFAULT NULL COMMENT '备注（口味要求等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `batch_no` int NULL DEFAULT 1 COMMENT '批次号(第几次下单，1表示首次，2表示第二次加菜)',
  `submitted_by_user_id` bigint NULL COMMENT '本批确认提交订单的用户ID',
  `submitted_by_name` varchar(100) NULL COMMENT '本批确认提交订单的用户名称快照',
  `add_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加菜时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_order_batch`(`order_id`, `batch_no`),
  INDEX `idx_tenant_order_dish`(`tenant_id`, `order_id`, `dish_id`),
  INDEX `idx_dish_gift`(`dish_id`, `is_gift`),
  INDEX `idx_category_gift`(`category_id`, `is_gift`)
) ENGINE = InnoDB COMMENT = '订单商品表';

-- ----------------------------
-- 租户经营分析日汇总
-- ----------------------------
DROP TABLE IF EXISTS `analysis_store_daily`;
CREATE TABLE `analysis_store_daily` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `biz_date` date NOT NULL COMMENT '支付归属自然日',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `gross_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '退菜后、优惠前销售总额，单位：元',
  `net_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '优惠退款后的实收金额，单位：元',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额，单位：元',
  `valid_order_count` bigint NOT NULL DEFAULT 0 COMMENT '有效支付订单数',
  `sales_quantity` bigint NOT NULL DEFAULT 0 COMMENT '非赠品净销量',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总更新时间',
  PRIMARY KEY (`tenant_id`, `biz_date`, `store_id`),
  INDEX `idx_analysis_store_date` (`tenant_id`, `store_id`, `biz_date`)
) ENGINE=InnoDB COMMENT='租户经营分析门店日汇总';

DROP TABLE IF EXISTS `analysis_store_dish_daily`;
CREATE TABLE `analysis_store_dish_daily` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `biz_date` date NOT NULL COMMENT '支付归属自然日',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `category_id` bigint NOT NULL DEFAULT 0 COMMENT '成交时分类ID；0表示未分类',
  `sales_quantity` bigint NOT NULL DEFAULT 0 COMMENT '非赠品净销量',
  `gross_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '非赠品优惠前销售额，单位：元',
  `net_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '订单项优惠退款后金额，单位：元',
  `cost_amount` decimal(18,2) NULL COMMENT '成交成本总额；成本不完整时仅用于内部核对',
  `gross_profit_amount` decimal(18,2) NULL COMMENT '毛利额；存在未知成本时为空',
  `order_count` bigint NOT NULL DEFAULT 0 COMMENT '包含该菜品的去重订单数',
  `gift_quantity` bigint NOT NULL DEFAULT 0 COMMENT '赠送数量',
  `unknown_cost_quantity` bigint NOT NULL DEFAULT 0 COMMENT '未维护成交成本的数量',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总更新时间',
  PRIMARY KEY (`tenant_id`, `biz_date`, `store_id`, `dish_id`, `category_id`),
  INDEX `idx_analysis_dish_date` (`tenant_id`, `dish_id`, `biz_date`, `store_id`),
  INDEX `idx_analysis_store_dish_date` (`tenant_id`, `store_id`, `dish_id`, `biz_date`)
) ENGINE=InnoDB COMMENT='租户经营分析门店菜品日汇总';

DROP TABLE IF EXISTS `order_dish_change_log`;
CREATE TABLE `order_dish_change_log` (
  `id` bigint NOT NULL COMMENT '事件ID',
  `event_key` varchar(80) NOT NULL COMMENT '租户内幂等事件键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint NULL COMMENT '订单项ID；订单项删除后仍保留',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID快照',
  `dish_name` varchar(100) NULL COMMENT '菜品名称快照',
  `category_id` bigint NULL COMMENT '分类ID快照',
  `change_type` tinyint NOT NULL COMMENT '变更类型：1加菜，2退菜',
  `quantity` int NOT NULL COMMENT '变更数量，保存正数',
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '加菜成交金额或退菜金额',
  `reason` varchar(255) NULL COMMENT '变更原因',
  `operator_id` bigint NULL COMMENT '加退菜操作人ID',
  `operator_name` varchar(100) NULL COMMENT '加退菜操作人名称快照',
  `occurred_at` datetime NOT NULL COMMENT '业务发生时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_event_key` (`tenant_id`,`event_key`),
  KEY `idx_change_analysis` (`tenant_id`,`store_id`,`occurred_at`,`change_type`,`dish_id`),
  KEY `idx_change_order` (`tenant_id`,`order_id`,`order_item_id`)
) ENGINE=InnoDB COMMENT='订单加退菜事件日志';

CREATE TABLE `order_price_adjustment` (
  `id` bigint NOT NULL COMMENT '价格调整ID（雪花ID）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint NOT NULL COMMENT '订单明细ID',
  `campaign_id` bigint NULL COMMENT '定价活动ID；会员价或人工调整时为空',
  `adjustment_type` tinyint NOT NULL COMMENT '调整类型：0会员价，1直接促销，2第N件优惠，3订单优惠，4赠品，5人工折扣',
  `before_amount` decimal(12,2) NOT NULL COMMENT '调整前金额，单位：元',
  `adjustment_amount` decimal(12,2) NOT NULL COMMENT '正数表示减免',
  `after_amount` decimal(12,2) NOT NULL COMMENT '调整后金额，单位：元',
  `rule_snapshot` json NULL COMMENT '下单时规则不可变快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_adjustment_order` (`tenant_id`,`order_id`),
  KEY `idx_adjustment_item` (`tenant_id`,`order_item_id`),
  KEY `idx_adjustment_campaign` (`tenant_id`,`campaign_id`)
) ENGINE=InnoDB COMMENT='订单价格调整快照';

-- ----------------------------
-- 飞鹅打印机信息表
-- ----------------------------
DROP TABLE IF EXISTS `printer_info`;
CREATE TABLE `printer_info` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `sn` varchar(64) NOT NULL COMMENT '打印机编号（飞鹅云打印机SN）',
  `printer_key` varchar(64) NOT NULL COMMENT '打印机识别码（飞鹅云打印机KEY）',
  `printer_brand` tinyint NULL DEFAULT NULL COMMENT '打印机品牌：1飞鹅，2其他',
  `remark` varchar(128) NULL DEFAULT NULL COMMENT '打印机备注名称（如：前台打印机、后厨打印机）',
  `card_num` varchar(32) NULL DEFAULT NULL COMMENT '流量卡号码（选填）',
  `store_id` bigint NULL DEFAULT NULL COMMENT '关联的门店ID',
  `template_id` bigint NULL DEFAULT NULL COMMENT '小票模板id',
  `printer_type` tinyint NULL DEFAULT 1 COMMENT '打印机类型：1-小票机，2-标签机',
  `status` tinyint NULL DEFAULT 0 COMMENT '打印机状态：0-离线，1-在线，2-缺纸',
  `last_online_time` datetime NULL DEFAULT NULL COMMENT '最后在线时间',
  `is_default` tinyint NULL DEFAULT 0 COMMENT '是否为默认打印机：0-否，1-是',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_sn`(`sn`),
  INDEX `idx_store_id`(`store_id`)
) ENGINE = InnoDB COMMENT = '飞鹅打印机信息表';

-- ----------------------------
-- 打印模板配置表
-- ----------------------------
DROP TABLE IF EXISTS `print_template`;
CREATE TABLE `print_template` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `store_id` bigint NULL DEFAULT NULL COMMENT '关联的门店ID（为空表示全局模板）',
  `paper_width` tinyint NOT NULL DEFAULT 0 COMMENT '纸张宽度：0为58mm，1为80mm',
  `show_logo` tinyint NULL DEFAULT 0 COMMENT '是否显示店铺Logo：0-不显示，1-显示',
  `logo_url` varchar(500) NULL DEFAULT NULL COMMENT 'Logo图片URL',
  `store_name_style` tinyint NULL DEFAULT 2 COMMENT '店铺名称显示样式：0-普通，1-加粗，2-加粗+居中，3-加粗+居中+放大',
  `show_address` tinyint NULL DEFAULT 1 COMMENT '是否显示店铺地址：0-不显示，1-显示',
  `show_phone` tinyint NULL DEFAULT 1 COMMENT '是否显示联系电话：0-不显示，1-显示',
  `show_business_hours` tinyint NULL DEFAULT 0 COMMENT '是否显示营业时间：0-不显示，1-显示',
  `order_info_fields` varchar(500) NULL DEFAULT '["orderNo","tableName","orderType","createdAt"]' COMMENT '订单信息显示字段（JSON数组）',
  `dish_list_style` tinyint NULL DEFAULT 0 COMMENT '菜品列表显示样式：0-简约，1-详细（含规格、备注）',
  `show_dish_image` tinyint NULL DEFAULT 0 COMMENT '是否显示菜品图片：0-不显示，1-显示',
  `show_dish_spec` tinyint NULL DEFAULT 1 COMMENT '是否显示菜品规格：0-不显示，1-显示',
  `show_dish_note` tinyint NULL DEFAULT 1 COMMENT '是否显示菜品备注：0-不显示，1-显示',
  `show_order_note` tinyint NULL DEFAULT 1 COMMENT '是否显示订单备注：0-不显示，1-显示',
  `show_payment_info` tinyint NULL DEFAULT 1 COMMENT '是否显示支付信息：0-不显示，1-显示',
  `footer_text` varchar(200) NULL DEFAULT '谢谢惠顾，欢迎再次光临！' COMMENT '底部欢迎语',
  `show_qrcode` tinyint NULL DEFAULT 1 COMMENT '是否显示二维码：0-不显示，1-显示',
  `qrcode_template` varchar(500) NULL DEFAULT 'http://yourdomain.com/order/{orderNo}' COMMENT '二维码内容模板',
  `qrcode_size` tinyint NULL DEFAULT 2 COMMENT '二维码大小：1-小，2-中，3-大',
  `font_size` tinyint NULL DEFAULT 0 COMMENT '字体大小：0-正常，1-中号，2-大号',
  `print_density` tinyint NULL DEFAULT 8 COMMENT '打印浓度（1-15，默认8）',
  `print_copies` tinyint NULL DEFAULT 1 COMMENT '打印联数（默认1）',
  `is_default` tinyint NULL DEFAULT 0 COMMENT '是否为默认模板：0-否，1-是',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '模板备注说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `show_pickup_code` tinyint NULL DEFAULT NULL COMMENT '是否展示取餐码',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_store_id`(`store_id`)
) ENGINE = InnoDB COMMENT = '打印模板配置表';
