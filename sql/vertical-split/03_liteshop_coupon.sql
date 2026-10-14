-- ============================================================
-- liteshop_coupon  —— 优惠券服务库（liteShop-coupon-service）
-- 变更说明（相对 hongtu 单库）：
--   1. 主键改雪花 ID：bigint，去掉 AUTO_INCREMENT / UNSIGNED
--   2. 移除全部外键（原 user_coupon -> sys_user 为跨库外键，必须移除）
--   3. 全部表末尾追加 tenant_id（多租户预留）
--   4. 统一排序规则 utf8mb4_0900_ai_ci
--   5. 修复 create_time / obtain_time 误加 ON UPDATE CURRENT_TIMESTAMP 的问题
--   6. 索引优化：user_coupon 补充 code 查询索引与过期扫描索引（status, expire_time）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `liteshop_coupon` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `liteshop_coupon`;

-- ----------------------------
-- 优惠券
-- ----------------------------
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon` (
  `id` bigint NOT NULL COMMENT '优惠券ID（雪花ID）',
  `name` varchar(100) NOT NULL COMMENT '优惠券名称',
  `description` text NULL COMMENT '描述',
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '类型(0:满减券, 1:整单折扣券, 2:菜品免单券)',
  `value` decimal(10, 2) NOT NULL COMMENT '面值(折扣券存折扣率0.8)',
  `min_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '最低消费金额',
  `total_count` int NOT NULL COMMENT '发放总数',
  `remain_count` int NOT NULL COMMENT '剩余数量',
  `start_time` datetime NULL DEFAULT NULL COMMENT '有效期开始',
  `end_time` datetime NULL DEFAULT NULL COMMENT '有效期结束',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:下架,1:上架)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `stackable` tinyint NULL DEFAULT 1 COMMENT '是否可叠加(0不可,1可)',
  `exclusive_group` int NULL DEFAULT NULL COMMENT '互斥组ID(同组互斥)',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_tenant_status`(`tenant_id`, `status`),
  INDEX `idx_tenant_status_end` (`tenant_id`, `status`, `end_time`)
) ENGINE = InnoDB COMMENT = '优惠券';

-- ----------------------------
-- 优惠券-菜品关联表（套餐券/团购券/菜品券的菜品明细）
-- ----------------------------
DROP TABLE IF EXISTS `coupon_dish_item`;
CREATE TABLE `coupon_dish_item` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `coupon_id` bigint NOT NULL COMMENT '优惠券ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_coupon_id`(`coupon_id`),
  INDEX `idx_dish_id`(`dish_id`)
) ENGINE = InnoDB COMMENT = '优惠券-菜品关联表';

-- ----------------------------
-- 用户优惠券关联表
-- ----------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `coupon_id` bigint NOT NULL COMMENT '优惠券ID',
  `code` varchar(20) NULL DEFAULT NULL COMMENT '优惠券唯一码',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0:未生效,1:未使用,2:已使用,3:已过期)',
  `obtain_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `use_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_user_status`(`user_id`, `status`),
  INDEX `idx_user_coupon`(`user_id`, `coupon_id`),
  INDEX `idx_coupon_id`(`coupon_id`),
  INDEX `idx_code`(`code`),
  INDEX `idx_status_expire`(`status`, `expire_time`),
  INDEX `idx_tenant_status_expire` (`tenant_id`, `status`, `expire_time`),
  INDEX `idx_tenant_status_start` (`tenant_id`, `status`, `start_time`)
) ENGINE = InnoDB COMMENT = '用户优惠券关联表';

-- ----------------------------
-- 核销记录
-- ----------------------------
DROP TABLE IF EXISTS `verify_record`;
CREATE TABLE `verify_record` (
  `id` bigint NOT NULL COMMENT '核销ID（雪花ID）',
  `user_coupon_id` bigint NOT NULL COMMENT '用户优惠券ID',
  `order_id` bigint NULL DEFAULT NULL COMMENT '订单核销来源，人工核销为空',
  `merchant_id` bigint NULL DEFAULT NULL COMMENT '核销管理员ID',
  `verify_code` varchar(50) NOT NULL COMMENT '核销码',
  `verify_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  `status` tinyint NULL DEFAULT NULL COMMENT '核销状态 0：未核销 1：已核销',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_verify_code`(`verify_code`),
  INDEX `idx_merchant_time`(`merchant_id`, `verify_time`),
  INDEX `idx_user_coupon_id`(`user_coupon_id`),
  UNIQUE INDEX `uk_tenant_coupon_order`(`tenant_id`, `user_coupon_id`, `order_id`)
) ENGINE = InnoDB COMMENT = '核销记录';

-- ----------------------------
-- 优惠券消息消费幂等凭据
-- ----------------------------
DROP TABLE IF EXISTS `coupon_message_receipt`;
CREATE TABLE `coupon_message_receipt` (
  `event_id` varchar(64) NOT NULL COMMENT '业务事件唯一键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`event_id`),
  INDEX `idx_tenant_created`(`tenant_id`, `created_at`)
) ENGINE = InnoDB COMMENT = '优惠券消息消费幂等凭据';
