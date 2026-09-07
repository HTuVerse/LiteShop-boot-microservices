/*
 Navicat Premium Dump SQL

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 80039 (8.0.39)
 Source Host           : localhost:3306
 Source Schema         : hongtu

 Target Server Type    : MySQL
 Target Server Version : 80039 (8.0.39)
 File Encoding         : 65001

 Date: 27/08/2026 16:32:35
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for activity_prize
-- ----------------------------
DROP TABLE IF EXISTS `activity_prize`;
CREATE TABLE `activity_prize`  (
  `activity_id` int UNSIGNED NOT NULL COMMENT '活动id',
  `prize_id` int UNSIGNED NOT NULL COMMENT '奖品id',
  `total_stock` bigint NULL DEFAULT NULL COMMENT '总库存',
  `surplus_stock` bigint NULL DEFAULT NULL COMMENT '剩余库存',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  INDEX `fk_prize_id`(`prize_id` ASC) USING BTREE,
  INDEX `fk_activity_id`(`activity_id` ASC) USING BTREE,
  CONSTRAINT `fk_activity_id` FOREIGN KEY (`activity_id`) REFERENCES `lottery_activity` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_prize_id` FOREIGN KEY (`prize_id`) REFERENCES `lottery_prize` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '活动奖品关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for coupon
-- ----------------------------
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '描述',
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '类型(0:满减券, 1:整单折扣券, 2:菜品免单券)',
  `value` decimal(10, 2) NOT NULL COMMENT '面值(折扣券存折扣率0.8)',
  `min_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '最低消费金额',
  `total_count` int NOT NULL COMMENT '发放总数',
  `remain_count` int NOT NULL COMMENT '剩余数量',
  `start_time` datetime NULL DEFAULT NULL COMMENT '有效期开始',
  `end_time` datetime NULL DEFAULT NULL COMMENT '有效期结束',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:下架,1:上架)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `stackable` tinyint(1) NULL DEFAULT 1 COMMENT '是否可叠加(0不可,1可)',
  `exclusive_group` int NULL DEFAULT NULL COMMENT '互斥组ID(同组互斥)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '优惠券' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for coupon_dish_item
-- ----------------------------
DROP TABLE IF EXISTS `coupon_dish_item`;
CREATE TABLE `coupon_dish_item`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `coupon_id` bigint UNSIGNED NOT NULL COMMENT '优惠券ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_coupon_id`(`coupon_id` ASC) USING BTREE,
  INDEX `idx_dish_id`(`dish_id` ASC) USING BTREE,
  CONSTRAINT `coupon_dish_item_0001` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '优惠券-菜品关联表（套餐券/团购券/菜品券的菜品明细）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dish
-- ----------------------------
DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `dish_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '菜品名称',
  `dish_desc` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '菜品描述',
  `dish_pics` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '菜品图片（JSON数组）',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '活动价',
  `original_price` decimal(10, 2) NOT NULL COMMENT '原价',
  `cost_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '成本价',
  `unit` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '份' COMMENT '单位',
  `stock` int NOT NULL DEFAULT 999 COMMENT '库存数量',
  `sales_count` int NOT NULL DEFAULT 0 COMMENT '销量',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览量',
  `is_recommend` tinyint NOT NULL DEFAULT 0 COMMENT '是否推荐：0-否，1-是',
  `is_hot` tinyint NOT NULL DEFAULT 0 COMMENT '是否热门：0-否，1-是',
  `is_new` tinyint NOT NULL DEFAULT 0 COMMENT '是否新品：0-否，1-是',
  `scene_type` tinyint NOT NULL DEFAULT 3 COMMENT '适用场景：0-堂食，1-外卖，2-通用',
  `sale_status` tinyint NOT NULL DEFAULT 1 COMMENT '销售状态：0-下架，1-上架',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，数字越小越靠前',
  `tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商品标签（JSON数组）',
  `allergens` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '过敏原信息',
  `nutrition_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '营养信息（JSON）',
  `cooking_time` int NULL DEFAULT 0 COMMENT '制作时间（分钟）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_category_id`(`category_id` ASC) USING BTREE,
  INDEX `idx_sale_status`(`sale_status` ASC) USING BTREE,
  INDEX `idx_scene_type`(`scene_type` ASC) USING BTREE,
  INDEX `idx_sort_order`(`sort_order` ASC) USING BTREE,
  INDEX `idx_recommend`(`is_recommend` ASC) USING BTREE,
  INDEX `idx_hot`(`is_hot` ASC) USING BTREE,
  INDEX `idx_new`(`is_new` ASC) USING BTREE,
  INDEX `idx_store_category_status`(`store_id` ASC, `category_id` ASC, `sale_status` ASC) USING BTREE,
  INDEX `idx_scene_status_sort`(`scene_type` ASC, `sale_status` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_store_sale_status`(`store_id` ASC, `sale_status` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_tags`(`is_recommend` ASC, `is_hot` ASC, `is_new` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dish_category
-- ----------------------------
DROP TABLE IF EXISTS `dish_category`;
CREATE TABLE `dish_category`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店ID',
  `category_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名称',
  `category_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类描述',
  `category_pic` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类图片',
  `category_icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类icon',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '分类层级：1-一级，2-二级',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，数字越小越靠前',
  `is_show` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示：0-隐藏，1-显示',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_sort_order`(`sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜品分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dish_spec_option
-- ----------------------------
DROP TABLE IF EXISTS `dish_spec_option`;
CREATE TABLE `dish_spec_option`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `spec_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规格名称（如：分量、做法、温度）',
  `spec_values` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '可选值JSON数组（如：[{\"value\"{\"value\":\"小份\",\"extra_price\":0}]）',
  `is_required` tinyint NOT NULL DEFAULT 1 COMMENT '是否必选：0-否，1-是',
  `more_select` tinyint NOT NULL DEFAULT 0 COMMENT '是否多选：0-否，1-是',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dish_id`(`dish_id` ASC) USING BTREE,
  INDEX `idx_sort_order`(`sort_order` ASC) USING BTREE,
  INDEX `idx_dish_status`(`dish_id` ASC, `status` ASC) USING BTREE,
  CONSTRAINT `dish_spec_foreign_key_0001` FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜品规格选项表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_upload
-- ----------------------------
DROP TABLE IF EXISTS `file_upload`;
CREATE TABLE `file_upload`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `original_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件原始名称',
  `size` bigint NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `content_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件类型',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件访问URL',
  `storage_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '存储类型（local/oss）',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件上传' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lottery_activity
-- ----------------------------
DROP TABLE IF EXISTS `lottery_activity`;
CREATE TABLE `lottery_activity`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '活动id',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '活动名字',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态[0:关闭；1启用]',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '活动描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签',
  `img_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图片地址',
  `start_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动开始时间',
  `end_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '活动结束时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '活动更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '抽奖活动表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lottery_prize
-- ----------------------------
DROP TABLE IF EXISTS `lottery_prize`;
CREATE TABLE `lottery_prize`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '奖品ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '奖品名称',
  `img_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '奖品图片URL',
  `probability` decimal(10, 2) NOT NULL COMMENT '中奖概率(百分比)',
  `type` tinyint NOT NULL COMMENT '奖品类型: 0-优惠券, 1-积分, 2-实物, 3-谢谢参与',
  `coupon_id` bigint NULL DEFAULT NULL COMMENT '关联的优惠券ID',
  `points` int NULL DEFAULT NULL COMMENT '积分数量',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用[0:禁用；1:启用]',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '抽奖奖品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lottery_record
-- ----------------------------
DROP TABLE IF EXISTS `lottery_record`;
CREATE TABLE `lottery_record`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `prize_id` int UNSIGNED NOT NULL COMMENT '奖品ID',
  `prize_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '奖品名称',
  `prize_type` tinyint NOT NULL COMMENT '奖品类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_received` tinyint(1) NULL DEFAULT NULL COMMENT '是否已领取：[0:否；1:是]',
  `receive_time` datetime NULL DEFAULT NULL COMMENT '领取时间',
  `activity_id` int UNSIGNED NOT NULL COMMENT '归属活动',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `fk_prize_id_1`(`prize_id` ASC) USING BTREE,
  CONSTRAINT `fk_prize_id_1` FOREIGN KEY (`prize_id`) REFERENCES `lottery_prize` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_user_id_2` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 135 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '抽奖记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for print_template
-- ----------------------------
DROP TABLE IF EXISTS `print_template`;
CREATE TABLE `print_template`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  `store_id` bigint NULL DEFAULT NULL COMMENT '关联的门店ID（为空表示全局模板）',
  `paper_width` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '58mm' COMMENT '纸张宽度类型：58mm、80mm',
  `show_logo` tinyint NULL DEFAULT 0 COMMENT '是否显示店铺Logo：0-不显示，1-显示',
  `logo_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Logo图片URL',
  `store_name_style` tinyint NULL DEFAULT 2 COMMENT '店铺名称显示样式：0-普通，1-加粗，2-加粗+居中，3-加粗+居中+放大',
  `show_address` tinyint NULL DEFAULT 1 COMMENT '是否显示店铺地址：0-不显示，1-显示',
  `show_phone` tinyint NULL DEFAULT 1 COMMENT '是否显示联系电话：0-不显示，1-显示',
  `show_business_hours` tinyint NULL DEFAULT 0 COMMENT '是否显示营业时间：0-不显示，1-显示',
  `order_info_fields` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '[\"orderNo\",\"tableName\",\"orderType\",\"createdAt\"]' COMMENT '订单信息显示字段（JSON数组）',
  `dish_list_style` tinyint NULL DEFAULT 0 COMMENT '菜品列表显示样式：0-简约（仅名称、数量、价格），1-详细（含规格、备注）',
  `show_dish_image` tinyint NULL DEFAULT 0 COMMENT '是否显示菜品图片：0-不显示，1-显示',
  `show_dish_spec` tinyint NULL DEFAULT 1 COMMENT '是否显示菜品规格：0-不显示，1-显示',
  `show_dish_note` tinyint NULL DEFAULT 1 COMMENT '是否显示菜品备注：0-不显示，1-显示',
  `show_order_note` tinyint NULL DEFAULT 1 COMMENT '是否显示订单备注：0-不显示，1-显示',
  `show_payment_info` tinyint NULL DEFAULT 1 COMMENT '是否显示支付信息：0-不显示，1-显示',
  `footer_text` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '谢谢惠顾，欢迎再次光临！' COMMENT '底部欢迎语',
  `show_qrcode` tinyint NULL DEFAULT 1 COMMENT '是否显示二维码：0-不显示，1-显示',
  `qrcode_template` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'http://yourdomain.com/order/{orderNo}' COMMENT '二维码内容模板',
  `qrcode_size` tinyint NULL DEFAULT 2 COMMENT '二维码大小：1-小，2-中，3-大',
  `font_size` tinyint NULL DEFAULT 0 COMMENT '字体大小：0-正常，1-中号，2-大号',
  `print_density` tinyint NULL DEFAULT 8 COMMENT '打印浓度（1-15，默认8）',
  `print_copies` tinyint NULL DEFAULT 1 COMMENT '打印联数（默认1）',
  `is_default` tinyint NULL DEFAULT 0 COMMENT '是否为默认模板：0-否，1-是',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板备注说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `show_pickup_code` tinyint NULL DEFAULT NULL COMMENT '是否展示取餐码',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_is_default`(`is_default` ASC) USING BTREE,
  INDEX `idx_enabled`(`enabled` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '打印模板配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for printer_info
-- ----------------------------
DROP TABLE IF EXISTS `printer_info`;
CREATE TABLE `printer_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sn` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '打印机编号（飞鹅云打印机SN）',
  `printer_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '打印机识别码（飞鹅云打印机KEY）',
  `printer_brand` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '打印机品牌',
  `remark` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '打印机备注名称（如：前台打印机、后厨打印机）',
  `card_num` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '流量卡号码（选填）',
  `store_id` bigint NULL DEFAULT NULL COMMENT '关联的门店ID',
  `template_id` bigint NULL DEFAULT NULL COMMENT '小票模板id',
  `printer_type` tinyint NULL DEFAULT 1 COMMENT '打印机类型：1-小票机，2-标签机',
  `status` tinyint NULL DEFAULT 0 COMMENT '打印机状态：0-离线，1-在线，2-缺纸',
  `last_online_time` datetime NULL DEFAULT NULL COMMENT '最后在线时间',
  `is_default` tinyint NULL DEFAULT 0 COMMENT '是否为默认打印机：0-否，1-是',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sn`(`sn` ASC) USING BTREE,
  INDEX `idx_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_is_default`(`is_default` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '飞鹅打印机信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for restaurant_order
-- ----------------------------
DROP TABLE IF EXISTS `restaurant_order`;
CREATE TABLE `restaurant_order`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单编号',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_id` bigint NULL DEFAULT NULL COMMENT '餐桌ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID（可选）',
  `session_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '会话编号',
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
  `payment_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付流水号',
  `pickup_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '取餐码',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '订单状态：1-已下单，2-已接单，3-制作中，4-制作完成，5-已完成，6-已取消',
  `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单备注',
  `cancel_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '取消原因',
  `cancel_time` datetime NULL DEFAULT NULL COMMENT '取消时间',
  `service_fee` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '服务费',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dining_end_time` datetime NULL DEFAULT NULL COMMENT '用餐结束时间',
  `user_coupon_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '对应 user_coupon 表的 id',
  `coupon_types` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_table_id`(`table_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_store_table_status`(`store_id` ASC, `table_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_user_session_status`(`user_id` ASC, `session_no` ASC, `status` ASC) USING BTREE,
  INDEX `idx_session_no`(`session_no` ASC) USING BTREE,
  INDEX `idx_store_time_payment_status`(`store_id` ASC, `created_at` ASC, `payment_status` ASC, `status` ASC) USING BTREE,
  INDEX `idx_store_created_at`(`store_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_store_status_time`(`store_id` ASC, `status` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_store_cancel_time`(`store_id` ASC, `status` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_store_payment_time`(`store_id` ASC, `payment_status` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_payment_time`(`payment_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 229 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '餐厅订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for restaurant_order_item
-- ----------------------------
DROP TABLE IF EXISTS `restaurant_order_item`;
CREATE TABLE `restaurant_order_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `category_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类id',
  `price` decimal(10, 2) NOT NULL COMMENT '单价',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `discount_price` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '优惠金额',
  `total_price` decimal(10, 2) NOT NULL COMMENT '小计',
  `is_gift` tinyint(1) NULL DEFAULT 0 COMMENT '是否赠送（0-否，1-是）',
  `spec_options_text` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '选择的规格',
  `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注（口味要求等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `batch_no` int NULL DEFAULT 1 COMMENT '批次号(第几次下单，1表示首次，2表示第二次加菜)',
  `add_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加菜时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_product_id`(`dish_id` ASC) USING BTREE,
  INDEX `idx_order_batch`(`order_id` ASC, `batch_no` ASC) USING BTREE,
  INDEX `idx_dish_gift`(`dish_id` ASC, `is_gift` ASC) USING BTREE,
  INDEX `idx_category_gift`(`category_id` ASC, `is_gift` ASC) USING BTREE,
  INDEX `idx_order_dish`(`order_id` ASC, `dish_id` ASC) USING BTREE,
  INDEX `idx_batch_no`(`batch_no` ASC) USING BTREE,
  INDEX `idx_add_time`(`add_time` ASC) USING BTREE,
  CONSTRAINT `rest_order_foreign_key_0001` FOREIGN KEY (`order_id`) REFERENCES `restaurant_order` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for store_info
-- ----------------------------
DROP TABLE IF EXISTS `store_info`;
CREATE TABLE `store_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '门店名称',
  `store_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '门店编码',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '门店地址',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `business_hours` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '营业时间',
  `payment_mode` tinyint NOT NULL DEFAULT 1 COMMENT '支付模式：1-线上支付，2-线下支付',
  `pickup_mode` tinyint NULL DEFAULT NULL COMMENT '取餐模式：0-送上桌，1-叫号自取',
  `is_print` tinyint NULL DEFAULT NULL COMMENT '是否开启小票打印：1:是，0:否',
  `allow_pre_order` tinyint NOT NULL DEFAULT 1 COMMENT '是否允许预点单：0-否，1-是',
  `pre_order_limit` int NULL DEFAULT 10 COMMENT '预点单时间限制（分钟）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-停业，1-营业，2-休息中',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '门店简介',
  `store_images` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '门店图片',
  `longitude` double NULL DEFAULT NULL COMMENT '经度（用于地图定位）',
  `latitude` double NULL DEFAULT NULL COMMENT '纬度（用于地图定位）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_store_code`(`store_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '门店信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_banner
-- ----------------------------
DROP TABLE IF EXISTS `sys_banner`;
CREATE TABLE `sys_banner`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标题',
  `image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片地址',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '跳转链接',
  `link_type` tinyint NULL DEFAULT 3 COMMENT '跳转类型：1-小程序页面 2-H5链接 3-不跳转',
  `position` tinyint NOT NULL COMMENT '轮播图位置：1-首页轮播图 2-点餐页轮播图 3-个人中心轮播图 4-活动页轮播图',
  `sort_order` int NULL DEFAULT 0 COMMENT '排序值，数字越小越靠前',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间（用于定时展示）',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间（用于定时展示）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_position_status`(`position` ASC, `status` ASC) USING BTREE,
  INDEX `idx_sort_order`(`sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '轮播图配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键（唯一）',
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '配置值（可存JSON）',
  `config_type` tinyint NULL DEFAULT 2 COMMENT '配置类型：1-图片 2-文本 3-JSON 4-颜色 5-数字',
  `config_group` tinyint NULL DEFAULT 5 COMMENT '配置分组：1-默认图片配置 2-应用信息配置 3-主题配置 4-联系方式配置 5-其他配置 6-部署配置',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置名称（中文说明）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置描述',
  `editable` tinyint NULL DEFAULT 1 COMMENT '是否允许修改：0-否 1-是',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_config_key`(`config_key` ASC) USING BTREE,
  INDEX `idx_config_group`(`config_group` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `permission_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限标识（如：system:user:add）',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父权限ID（0表示顶级）',
  `permission_type` tinyint NOT NULL DEFAULT 1 COMMENT '权限类型（1-菜单 2-按钮 3-API）',
  `component_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '组件地址',
  `router_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '路由地址',
  `sort` int NULL DEFAULT 0 COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1-启用 0-禁用）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'icon图标',
  `link` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'iframe 内嵌外部页面',
  `is_hide` tinyint NULL DEFAULT 0 COMMENT '是否在菜单中隐藏',
  `is_iframe` tinyint NULL DEFAULT 0 COMMENT '是否为iframe',
  `keep_alive` tinyint NULL DEFAULT 0 COMMENT '是否启用缓存',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `permission_code`(`permission_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 52 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码（如：ADMIN）',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态（1-启用 0-禁用）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `role_name`(`role_name` ASC) USING BTREE,
  UNIQUE INDEX `role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`  (
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_id` bigint UNSIGNED NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`role_id`, `permission_id`) USING BTREE,
  INDEX `sys_role_permission_ibfk_2`(`permission_id` ASC) USING BTREE,
  CONSTRAINT `sys_role_permission_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `sys_role_permission_ibfk_2` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录账号',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `gender` tinyint(1) UNSIGNED ZEROFILL NULL DEFAULT 0 COMMENT '性别[1:男，2:女，0:保密]',
  `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '电话',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `introduction` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户介绍',
  `exp` int NULL DEFAULT 0 COMMENT '用户经验值',
  `level` int NULL DEFAULT 1 COMMENT '用户等级',
  `points` int NULL DEFAULT 0 COMMENT '用户积分',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像地址',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态（1-正常 0-禁用）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `open_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '小程序唯一标识符',
  `surplus_money` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '用户余额',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 36 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `sys_user_role_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `sys_user_role_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for table_info
-- ----------------------------
DROP TABLE IF EXISTS `table_info`;
CREATE TABLE `table_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '餐桌编号',
  `table_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '餐桌名称',
  `qr_code` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '二维码内容',
  `qr_code_image` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '二维码图片Base64',
  `seats` int NOT NULL DEFAULT 4 COMMENT '座位数',
  `location` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '位置描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-空闲，1-使用中，2-预留，3-停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_store_table`(`store_id` ASC, `table_number` ASC) USING BTREE,
  INDEX `idx_store_id`(`store_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '餐桌信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for table_session
-- ----------------------------
DROP TABLE IF EXISTS `table_session`;
CREATE TABLE `table_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话编号',
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
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_no`(`session_no` ASC) USING BTREE,
  INDEX `idx_table_id`(`table_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`current_user_count` ASC) USING BTREE,
  INDEX `idx_store_table_status`(`store_id` ASC, `table_id` ASC, `status` ASC) USING BTREE,
  INDEX `foreign_tbs_0001`(`current_order_id` ASC) USING BTREE,
  CONSTRAINT `foreign_tbs_0001` FOREIGN KEY (`current_order_id`) REFERENCES `restaurant_order` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 49 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '餐桌会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_activity_chance
-- ----------------------------
DROP TABLE IF EXISTS `user_activity_chance`;
CREATE TABLE `user_activity_chance`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `activity_id` int UNSIGNED NOT NULL DEFAULT 1 COMMENT '抽奖活动ID',
  `chances` int NOT NULL DEFAULT 0 COMMENT '剩余抽奖次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_activity`(`user_id` ASC, `activity_id` ASC) USING BTREE,
  INDEX `fk_activity_id_1`(`activity_id` ASC) USING BTREE,
  CONSTRAINT `fk_activity_id_1` FOREIGN KEY (`activity_id`) REFERENCES `lottery_activity` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_user_id_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户抽奖机会活动关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_coupon
-- ----------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `coupon_id` bigint UNSIGNED NOT NULL COMMENT '优惠券ID',
  `code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '优惠券唯一码',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态状态(0:未生效,1:未使用,2:已使用,3:已过期)',
  `obtain_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '领取时间',
  `use_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_coupon`(`user_id` ASC, `coupon_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `fk_coupon`(`coupon_id` ASC) USING BTREE,
  INDEX `idx_user_status`(`user_id` ASC, `status` ASC) USING BTREE,
  CONSTRAINT `fk_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 33 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户优惠券关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_level
-- ----------------------------
DROP TABLE IF EXISTS `user_level`;
CREATE TABLE `user_level`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '等级ID',
  `level_code` int NOT NULL COMMENT '等级编码',
  `level_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '等级名称',
  `required_exp` int NOT NULL COMMENT '所需经验值',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '等级图标',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '等级描述',
  `benefits` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '等级权益',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用（1-启用，0-禁用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `sort` int NULL DEFAULT NULL COMMENT '显示顺序',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户等级配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_points_record
-- ----------------------------
DROP TABLE IF EXISTS `user_points_record`;
CREATE TABLE `user_points_record`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `operation_type` tinyint NOT NULL COMMENT '积分操作类型：1-增加，2-减少',
  `source` tinyint NOT NULL COMMENT '积分来源：1-注册奖励，2-签到奖励，3-购物奖励，4-活动奖励，5-积分兑换，6-其它',
  `points` int NOT NULL COMMENT '积分变动数量',
  `before_points` int NOT NULL COMMENT '操作前积分',
  `after_points` int NOT NULL COMMENT '操作后积分',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注说明',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_points_0001`(`user_id` ASC) USING BTREE,
  CONSTRAINT `user_points_0001` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 50 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户积分记录DTO' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_surplus_record
-- ----------------------------
DROP TABLE IF EXISTS `user_surplus_record`;
CREATE TABLE `user_surplus_record`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户id',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '订单号',
  `amount` decimal(12, 2) NOT NULL COMMENT '充值余额',
  `new_balance` decimal(12, 2) NOT NULL COMMENT '变动后余额',
  `type` tinyint NOT NULL COMMENT '1-充值 2-消费 3-退款',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '变动时间',
  `old_balance` decimal(10, 2) NOT NULL COMMENT '变动前余额',
  `operate_user_id` bigint NULL DEFAULT NULL COMMENT '操作用户id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_order`(`order_no` ASC) USING BTREE,
  CONSTRAINT `surplus_record_0001` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '余额变动历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for verify_record
-- ----------------------------
DROP TABLE IF EXISTS `verify_record`;
CREATE TABLE `verify_record`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '核销ID',
  `user_coupon_id` bigint UNSIGNED NOT NULL COMMENT '用户优惠券ID',
  `merchant_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '核销管理员ID',
  `verify_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '核销码',
  `verify_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  `status` tinyint NULL DEFAULT NULL COMMENT '核销状态 0：未核销 1：已核销',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_verify_code`(`verify_code` ASC) USING BTREE,
  INDEX `idx_merchant_time`(`merchant_id` ASC, `verify_time` ASC) USING BTREE,
  INDEX `fk_user_coupon`(`user_coupon_id` ASC) USING BTREE,
  CONSTRAINT `fk_user_coupon` FOREIGN KEY (`user_coupon_id`) REFERENCES `user_coupon` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 30 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '核销记录' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
