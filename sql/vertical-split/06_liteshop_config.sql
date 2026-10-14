-- ============================================================
-- liteshop_config  —— 配置服务库（liteShop-config-service）
-- 变更说明（相对 hongtu 单库）：
--   1. 主键改雪花 ID：bigint，去掉 AUTO_INCREMENT
--   2. 全部表末尾追加 tenant_id（多租户预留）
--   3. config_key 唯一键改为租户内唯一
-- ============================================================

CREATE DATABASE IF NOT EXISTS `liteshop_config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `liteshop_config`;

-- ----------------------------
-- 系统配置表
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `config_key` varchar(100) NOT NULL COMMENT '配置键（租户内唯一）',
  `config_value` text NULL COMMENT '配置值（可存JSON）',
  `config_type` tinyint NULL DEFAULT 2 COMMENT '配置类型：1-图片 2-文本 3-JSON 4-颜色 5-数字',
  `config_group` tinyint NULL DEFAULT 5 COMMENT '配置分组：1-默认图片配置 2-应用信息配置 3-主题配置 4-联系方式配置 5-其他配置 6-部署配置',
  `config_name` varchar(100) NULL DEFAULT NULL COMMENT '配置名称（中文说明）',
  `description` varchar(500) NULL DEFAULT NULL COMMENT '配置描述',
  `editable` tinyint NULL DEFAULT 1 COMMENT '是否允许修改：0-否 1-是',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_config_key`(`tenant_id`, `config_key`),
  INDEX `idx_config_group`(`config_group`)
) ENGINE = InnoDB COMMENT = '系统配置表';

-- ----------------------------
-- 轮播图配置表
-- ----------------------------
DROP TABLE IF EXISTS `sys_banner`;
CREATE TABLE `sys_banner` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `title` varchar(100) NULL DEFAULT NULL COMMENT '标题',
  `image_url` varchar(500) NOT NULL COMMENT '图片地址',
  `link_url` varchar(500) NULL DEFAULT NULL COMMENT '跳转链接',
  `link_type` tinyint NULL DEFAULT 3 COMMENT '跳转类型：1-小程序页面 2-H5链接 3-不跳转',
  `position` tinyint NOT NULL COMMENT '轮播图位置：1-首页轮播图 2-点餐页轮播图 3-个人中心轮播图 4-活动页轮播图',
  `sort_order` int NULL DEFAULT 0 COMMENT '排序值，数字越小越靠前',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间（用于定时展示）',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间（用于定时展示）',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_position_status`(`position`, `status`)
) ENGINE = InnoDB COMMENT = '轮播图配置表';

-- ----------------------------
-- 租户支付账户表
-- ----------------------------
DROP TABLE IF EXISTS `tenant_payment_account`;
CREATE TABLE `tenant_payment_account` (
  `id` bigint NOT NULL COMMENT '支付账户ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `channel` tinyint NOT NULL COMMENT '渠道：1微信，2支付宝',
  `enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用：0否，1是',
  `app_id` varchar(64) NOT NULL DEFAULT '' COMMENT '微信小程序或支付宝应用ID',
  `mch_id` varchar(64) NOT NULL DEFAULT '' COMMENT '微信商户号，支付宝为空',
  `mch_serial_no` varchar(128) NOT NULL DEFAULT '' COMMENT '微信商户API证书序列号，支付宝为空',
  `private_key_ciphertext` text NOT NULL COMMENT '加密后的商户私钥PEM或支付宝应用私钥',
  `api_v3_key_ciphertext` text NOT NULL COMMENT '加密后的微信APIv3密钥，支付宝为空',
  `alipay_public_key_ciphertext` text NOT NULL COMMENT '加密后的支付宝公钥，微信为空',
  `updated_by` bigint NULL COMMENT '最后修改的租户管理员用户ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_channel` (`tenant_id`, `channel`)
) ENGINE = InnoDB COMMENT = '租户支付账户';
