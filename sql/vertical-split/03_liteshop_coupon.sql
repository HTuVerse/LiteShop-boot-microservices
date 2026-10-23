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
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '类型(0:满减券, 1:整单折扣券, 2:菜品免单券, 3:菜品折扣券)',
  `value` decimal(10, 2) NOT NULL COMMENT '面值(折扣券存折扣率0.8)',
  `min_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '最低消费金额',
  `store_ids` varchar(2000) NULL COMMENT '适用门店ID，逗号分隔；空表示全部门店',
  `use_weekdays` varchar(13) NULL COMMENT '可用星期，1周一至7周日，逗号分隔；空表示每天',
  `use_start_time` time NULL COMMENT '每日可用开始时间；空表示全天',
  `use_end_time` time NULL COMMENT '每日可用结束时间；空表示全天',
  `total_count` int NOT NULL COMMENT '发放总数',
  `remain_count` int NOT NULL COMMENT '剩余数量',
  `start_time` datetime NULL DEFAULT NULL COMMENT '有效期开始',
  `end_time` datetime NULL DEFAULT NULL COMMENT '有效期结束',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:下架,1:上架)',
  `recall_time` datetime NULL COMMENT '模板批量回收时间；非空时永久停止发放',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `stackable` tinyint NULL DEFAULT 0 COMMENT '是否可叠加(0不可,1可；还需存在明确配对)',
  `exclusive_group` int NULL DEFAULT NULL COMMENT '互斥组ID(同组互斥)',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_tenant_status`(`tenant_id`, `status`),
  INDEX `idx_tenant_status_end` (`tenant_id`, `status`, `end_time`)
) ENGINE = InnoDB COMMENT = '优惠券';

-- 两张券只有配置明确配对、且双方均允许叠加时才能同单使用。
CREATE TABLE `coupon_stack_pair` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `coupon_id_low` bigint NOT NULL COMMENT '两张券中较小的券模板ID',
  `coupon_id_high` bigint NOT NULL COMMENT '两张券中较大的券模板ID',
  PRIMARY KEY (`tenant_id`, `coupon_id_low`, `coupon_id_high`),
  INDEX `idx_stack_high` (`tenant_id`, `coupon_id_high`)
) ENGINE = InnoDB COMMENT = '优惠券允许叠加配对表';

-- ----------------------------
-- 优惠券剩余可发库存调整记录
-- ----------------------------
DROP TABLE IF EXISTS `coupon_stock_adjustment`;
CREATE TABLE `coupon_stock_adjustment` (
  `id` bigint NOT NULL COMMENT '调整记录ID',
  `coupon_id` bigint NOT NULL COMMENT '优惠券ID',
  `delta` int NOT NULL COMMENT '剩余可发库存变动量，正数补库，负数减库，单位张',
  `before_count` int NOT NULL COMMENT '调整前剩余可发库存，单位张',
  `after_count` int NOT NULL COMMENT '调整后剩余可发库存，单位张',
  `operator_id` bigint NOT NULL COMMENT '操作管理员ID',
  `reason` varchar(200) NOT NULL COMMENT '调整原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_coupon_time` (`tenant_id`, `coupon_id`, `create_time`)
) ENGINE=InnoDB COMMENT='优惠券剩余可发库存调整记录';

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
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0:未生效,1:未使用,2:已使用,3:已过期,4:已回收)',
  `obtain_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `use_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `expire_time` datetime NULL COMMENT '过期时间；空表示长期有效',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_user_status`(`user_id`, `status`),
  INDEX `idx_user_coupon`(`user_id`, `coupon_id`),
  INDEX `idx_coupon_id`(`coupon_id`),
  UNIQUE INDEX `uk_tenant_coupon_code`(`tenant_id`, `code`),
  INDEX `idx_status_expire`(`status`, `expire_time`),
  INDEX `idx_tenant_status_expire` (`tenant_id`, `status`, `expire_time`),
  INDEX `idx_tenant_status_start` (`tenant_id`, `status`, `start_time`)
) ENGINE = InnoDB COMMENT = '用户优惠券关联表';

-- 模板回收任务持久化；中断后继续按批处理。
DROP TABLE IF EXISTS `coupon_revoke_batch`;
CREATE TABLE `coupon_revoke_batch` (
  `batch_id` varchar(36) NOT NULL COMMENT '回收任务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `coupon_id` bigint NOT NULL COMMENT '模板ID',
  `operator_id` bigint NULL COMMENT '操作人ID',
  `reason` varchar(200) NOT NULL COMMENT '回收原因',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0处理中，1已完成',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_tenant_revoke_coupon` (`tenant_id`, `coupon_id`),
  KEY `idx_tenant_revoke_status` (`tenant_id`, `status`, `updated_at`)
) ENGINE=InnoDB COMMENT='优惠券模板回收任务';

-- 每张回收券的操作记录，管理员可追溯批次、原因和操作人。
DROP TABLE IF EXISTS `coupon_revoke_record`;
CREATE TABLE `coupon_revoke_record` (
  `id` bigint NOT NULL COMMENT '回收记录ID',
  `user_coupon_id` bigint NOT NULL COMMENT '用户券ID',
  `coupon_id` bigint NOT NULL COMMENT '券模板ID',
  `user_id` bigint NOT NULL COMMENT '持券用户ID',
  `operator_id` bigint NULL COMMENT '操作管理员ID；系统取消订单回收时为空',
  `reason` varchar(200) NOT NULL COMMENT '回收原因',
  `batch_id` varchar(36) NULL COMMENT '模板批量回收批次号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回收时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user_coupon` (`tenant_id`, `user_coupon_id`),
  KEY `idx_tenant_coupon_time` (`tenant_id`, `coupon_id`, `create_time`)
) ENGINE=InnoDB COMMENT='用户券回收审计记录';

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
  UNIQUE INDEX `uk_tenant_verify_code`(`tenant_id`, `verify_code`),
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
  `lottery_notified_at` datetime NULL COMMENT '抽奖服务领取通知确认时间，空表示待重试',
  PRIMARY KEY (`event_id`),
  INDEX `idx_tenant_created`(`tenant_id`, `created_at`),
  INDEX `idx_lottery_notice`(`lottery_notified_at`, `created_at`)
) ENGINE = InnoDB COMMENT = '优惠券消息消费幂等凭据';
