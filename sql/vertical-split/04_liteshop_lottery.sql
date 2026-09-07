-- ============================================================
-- liteshop_lottery  —— 抽奖服务库（liteShop-lottery-service）
-- 变更说明（相对 hongtu 单库）：
--   1. 主键改雪花 ID：原 int UNSIGNED 主键全部升为 bigint（对应 Java 实体 Integer -> Long）
--   2. 移除全部外键（原 lottery_record/user_activity_chance -> sys_user 为跨库外键）
--   3. 全部表末尾追加 tenant_id（多租户预留）
--   4. activity_prize 原来没有主键，补充复合主键 (activity_id, prize_id)
--   5. 修复 create_time 误加 ON UPDATE CURRENT_TIMESTAMP、
--      lottery_activity.end_time 误加 ON UPDATE CURRENT_TIMESTAMP（会被意外刷新）的问题
-- ============================================================

CREATE DATABASE IF NOT EXISTS `liteshop_lottery` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `liteshop_lottery`;

-- ----------------------------
-- 抽奖活动表
-- ----------------------------
DROP TABLE IF EXISTS `lottery_activity`;
CREATE TABLE `lottery_activity` (
  `id` bigint NOT NULL COMMENT '活动id（雪花ID）',
  `name` varchar(255) NOT NULL COMMENT '活动名字',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态[0:关闭；1启用]',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '活动描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tag` varchar(255) NULL DEFAULT NULL COMMENT '标签',
  `img_url` varchar(255) NULL DEFAULT NULL COMMENT '图片地址',
  `start_time` datetime NULL DEFAULT NULL COMMENT '活动开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '活动结束时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '活动更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_tenant_status`(`tenant_id`, `status`)
) ENGINE = InnoDB COMMENT = '抽奖活动表';

-- ----------------------------
-- 抽奖奖品表
-- ----------------------------
DROP TABLE IF EXISTS `lottery_prize`;
CREATE TABLE `lottery_prize` (
  `id` bigint NOT NULL COMMENT '奖品ID（雪花ID）',
  `name` varchar(100) NOT NULL COMMENT '奖品名称',
  `img_url` varchar(255) NULL DEFAULT NULL COMMENT '奖品图片URL',
  `probability` decimal(10, 2) NOT NULL COMMENT '中奖概率(百分比)',
  `type` tinyint NOT NULL COMMENT '奖品类型: 0-优惠券, 1-积分, 2-实物, 3-谢谢参与',
  `coupon_id` bigint NULL DEFAULT NULL COMMENT '关联的优惠券ID',
  `points` int NULL DEFAULT NULL COMMENT '积分数量',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用[0:禁用；1:启用]',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = '抽奖奖品表';

-- ----------------------------
-- 活动奖品关联表（原表无主键，补充复合主键）
-- ----------------------------
DROP TABLE IF EXISTS `activity_prize`;
CREATE TABLE `activity_prize` (
  `activity_id` bigint NOT NULL COMMENT '活动id',
  `prize_id` bigint NOT NULL COMMENT '奖品id',
  `total_stock` bigint NULL DEFAULT NULL COMMENT '总库存',
  `surplus_stock` bigint NULL DEFAULT NULL COMMENT '剩余库存',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`activity_id`, `prize_id`),
  INDEX `idx_prize_id`(`prize_id`)
) ENGINE = InnoDB COMMENT = '活动奖品关联表';

-- ----------------------------
-- 抽奖记录表
-- ----------------------------
DROP TABLE IF EXISTS `lottery_record`;
CREATE TABLE `lottery_record` (
  `id` bigint NOT NULL COMMENT '记录ID（雪花ID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `prize_id` bigint NOT NULL COMMENT '奖品ID',
  `prize_name` varchar(100) NOT NULL COMMENT '奖品名称（冗余快照）',
  `prize_type` tinyint NOT NULL COMMENT '奖品类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_received` tinyint NULL DEFAULT NULL COMMENT '是否已领取：[0:否；1:是]',
  `receive_time` datetime NULL DEFAULT NULL COMMENT '领取时间',
  `activity_id` bigint NOT NULL COMMENT '归属活动',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_user_activity`(`user_id`, `activity_id`),
  INDEX `idx_activity_time`(`activity_id`, `create_time`)
) ENGINE = InnoDB COMMENT = '抽奖记录表';

-- ----------------------------
-- 用户抽奖机会活动关联表
-- ----------------------------
DROP TABLE IF EXISTS `user_activity_chance`;
CREATE TABLE `user_activity_chance` (
  `id` bigint NOT NULL COMMENT 'id（雪花ID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL DEFAULT 1 COMMENT '抽奖活动ID',
  `chances` int NOT NULL DEFAULT 0 COMMENT '剩余抽奖次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_activity`(`user_id`, `activity_id`),
  INDEX `idx_activity_id`(`activity_id`)
) ENGINE = InnoDB COMMENT = '用户抽奖机会活动关联表';
