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
--      - restaurant_order：原 13 个二级索引精简为 6 个
--        （idx_store_cancel_time 与 idx_store_status_time 完全重复等）
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
  INDEX `idx_parent_id`(`parent_id`)
) ENGINE = InnoDB COMMENT = '菜品分类表';

-- ----------------------------
-- 菜品表
-- ----------------------------
DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `dish_name` varchar(100) NOT NULL COMMENT '菜品名称',
  `dish_desc` text NULL COMMENT '菜品描述',
  `dish_pics` text NULL COMMENT '菜品图片（JSON数组）',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '活动价',
  `original_price` decimal(10, 2) NOT NULL COMMENT '原价',
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
  INDEX `idx_store_category_status`(`store_id`, `category_id`, `sale_status`),
  INDEX `idx_store_sale_status`(`store_id`, `sale_status`, `sort_order`),
  INDEX `idx_category_id`(`category_id`)
) ENGINE = InnoDB COMMENT = '菜品表';

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
  `qr_code` varchar(500) NULL DEFAULT NULL COMMENT '二维码内容',
  `qr_code_image` text NULL COMMENT '二维码图片Base64',
  `seats` int NOT NULL DEFAULT 4 COMMENT '座位数',
  `location` varchar(100) NULL DEFAULT NULL COMMENT '位置描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-空闲，1-使用中，2-预留，3-停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_store_table`(`store_id`, `table_number`)
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
  INDEX `idx_current_order_id`(`current_order_id`)
) ENGINE = InnoDB COMMENT = '餐桌会话表';

-- ----------------------------
-- 餐厅订单表
-- ----------------------------
DROP TABLE IF EXISTS `restaurant_order`;
CREATE TABLE `restaurant_order` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_id` bigint NULL DEFAULT NULL COMMENT '餐桌ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID（可选）',
  `session_no` varchar(64) NULL DEFAULT NULL COMMENT '会话编号',
  `order_type` tinyint NOT NULL DEFAULT 1 COMMENT '0-在线点餐 ，1-预点餐，2-门店自提',
  `dining_count` int NULL DEFAULT NULL COMMENT '就餐人数',
  `total_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
  `discount_amount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '优惠金额',
  `discount_rate` decimal(5, 2) NULL DEFAULT 1.00 COMMENT '整单折扣率（例如：0.8表示8折，1.0表示无折扣）',
  `actual_amount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '实际支付金额',
  `payment_mode` tinyint NOT NULL COMMENT '支付模式：1-线上支付，2-线下支付',
  `payment_status` tinyint NOT NULL DEFAULT 0 COMMENT '支付状态：0-未支付，1-已支付，2-部分退款，3-全额退款',
  `payment_type` tinyint NULL DEFAULT NULL COMMENT '支付方式：1-微信，2-支付宝，3-现金，4-银行卡',
  `payment_time` datetime NULL DEFAULT NULL COMMENT '支付时间',
  `payment_no` varchar(64) NULL DEFAULT NULL COMMENT '支付流水号',
  `pickup_code` varchar(32) NULL DEFAULT NULL COMMENT '取餐码',
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
  INDEX `idx_store_created_at`(`store_id`, `created_at`),
  INDEX `idx_store_status_time`(`store_id`, `status`, `created_at`),
  INDEX `idx_store_payment_time`(`store_id`, `payment_status`, `created_at`),
  INDEX `idx_store_table_status`(`store_id`, `table_id`, `status`),
  INDEX `idx_user_session_status`(`user_id`, `session_no`, `status`),
  INDEX `idx_session_no`(`session_no`),
  INDEX `idx_tenant_created`(`tenant_id`, `created_at`)
) ENGINE = InnoDB COMMENT = '餐厅订单表';

-- ----------------------------
-- 订单商品表
-- ----------------------------
DROP TABLE IF EXISTS `restaurant_order_item`;
CREATE TABLE `restaurant_order_item` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `category_id` varchar(50) NULL DEFAULT NULL COMMENT '分类id（历史遗留为字符串，建议后续改为 bigint）',
  `price` decimal(10, 2) NOT NULL COMMENT '单价',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `discount_price` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '优惠金额',
  `total_price` decimal(10, 2) NOT NULL COMMENT '小计',
  `is_gift` tinyint NULL DEFAULT 0 COMMENT '是否赠送（0-否，1-是）',
  `spec_options_text` varchar(255) NULL DEFAULT NULL COMMENT '选择的规格',
  `note` varchar(255) NULL DEFAULT NULL COMMENT '备注（口味要求等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `batch_no` int NULL DEFAULT 1 COMMENT '批次号(第几次下单，1表示首次，2表示第二次加菜)',
  `add_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加菜时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_order_batch`(`order_id`, `batch_no`),
  INDEX `idx_dish_gift`(`dish_id`, `is_gift`),
  INDEX `idx_category_gift`(`category_id`, `is_gift`)
) ENGINE = InnoDB COMMENT = '订单商品表';

-- ----------------------------
-- 飞鹅打印机信息表
-- ----------------------------
DROP TABLE IF EXISTS `printer_info`;
CREATE TABLE `printer_info` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `sn` varchar(64) NOT NULL COMMENT '打印机编号（飞鹅云打印机SN）',
  `printer_key` varchar(64) NOT NULL COMMENT '打印机识别码（飞鹅云打印机KEY）',
  `printer_brand` varchar(64) NULL DEFAULT NULL COMMENT '打印机品牌',
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
  `paper_width` varchar(20) NOT NULL DEFAULT '58mm' COMMENT '纸张宽度类型：58mm、80mm',
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
