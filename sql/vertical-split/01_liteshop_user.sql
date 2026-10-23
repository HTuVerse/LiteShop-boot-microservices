-- ============================================================
-- liteshop_user  —— 用户服务库（liteShop-user-service）
-- 变更说明（相对 hongtu 单库）：
--   1. 主键改雪花 ID：bigint，去掉 AUTO_INCREMENT（由 MyBatis-Plus ASSIGN_ID 生成）
--   2. 移除全部外键（拆库后跨库外键不可用；库内外键也不再保留，由应用层保证）
--   3. 平台账号、角色及其授权的 tenant_id 为空，商户账号按租户隔离；
--      sys_permission 为全局定义，user_level 由12号脚本升级为租户等级体系
--   4. 统一排序规则 utf8mb4_0900_ai_ci
--   5. 修复 create_time 误加 ON UPDATE CURRENT_TIMESTAMP 的问题
--   6. 索引优化：username/role_code 唯一键改为租户内唯一；补充 open_id/phone 索引
-- ============================================================

CREATE DATABASE IF NOT EXISTS `liteshop_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `liteshop_user`;

-- ----------------------------
-- 租户（商户）表：多租户主档。租户=商户/老板，一个租户可拥有多家门店。
-- id=1 为迁移时的默认租户（存量数据全部归属它）
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant` (
  `id` bigint NOT NULL COMMENT '租户ID（雪花ID）',
  `tenant_code` varchar(50) NOT NULL COMMENT '租户编码，用于登录与外部标识',
  `tenant_name` varchar(100) NOT NULL COMMENT '商户名称',
  `contact_name` varchar(50) NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) NULL DEFAULT NULL COMMENT '联系电话',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-正常',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '套餐到期时间（NULL 表示不限）',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_code`(`tenant_code`),
  UNIQUE INDEX `uk_tenant_name`(`tenant_name`)
) ENGINE = InnoDB COMMENT = '租户（商户）表';

-- ----------------------------
-- 系统用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '用户ID（雪花ID）',
  `username` varchar(50) NOT NULL COMMENT '登录账号',
  `password` varchar(100) NULL DEFAULT NULL COMMENT '密码',
  `nickname` varchar(50) NULL DEFAULT NULL COMMENT '用户昵称',
  `gender` tinyint NOT NULL DEFAULT 0 COMMENT '性别[1:男，2:女，0:保密]',
  `phone` varchar(20) NULL DEFAULT NULL COMMENT '电话',
  `email` varchar(100) NULL DEFAULT NULL COMMENT '邮箱',
  `introduction` varchar(255) NULL DEFAULT NULL COMMENT '用户介绍',
  `exp` int NULL DEFAULT 0 COMMENT '用户经验值',
  `level` int NULL DEFAULT 1 COMMENT '用户等级',
  `points` int NULL DEFAULT 0 COMMENT '用户积分',
  `avatar` varchar(255) NULL DEFAULT NULL COMMENT '头像地址',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1-正常 0-禁用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `open_id` varchar(64) NULL DEFAULT NULL COMMENT '小程序唯一标识符',
  `surplus_money` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '用户余额',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID；平台账号为空',
  `tenant_scope_id` bigint GENERATED ALWAYS AS (IFNULL(`tenant_id`,0)) STORED COMMENT '唯一索引租户范围，0表示平台',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_username`(`tenant_scope_id`, `username`),
  UNIQUE INDEX `uk_tenant_open_id`(`tenant_id`, `open_id`),
  UNIQUE INDEX `uk_tenant_phone`(`tenant_id`, `phone`),
  UNIQUE INDEX `uk_tenant_email`(`tenant_id`, `email`),
  INDEX `idx_open_id`(`open_id`),
  INDEX `idx_phone`(`phone`)
) ENGINE = InnoDB COMMENT = '系统用户表';

-- ----------------------------
-- 系统角色表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '角色ID（雪花ID）',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码（如：ADMIN）',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态（1-启用 0-禁用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID；平台角色为空',
  `tenant_scope_id` bigint GENERATED ALWAYS AS (IFNULL(`tenant_id`,0)) STORED COMMENT '唯一索引租户范围，0表示平台',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_role_code`(`tenant_scope_id`, `role_code`),
  UNIQUE INDEX `uk_tenant_role_name`(`tenant_id`, `role_name`)
) ENGINE = InnoDB COMMENT = '系统角色表';

-- ----------------------------
-- 系统权限表（全局菜单/权限定义，不区分租户）
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL COMMENT '权限ID（雪花ID）',
  `permission_name` varchar(100) NOT NULL COMMENT '权限名称',
  `permission_code` varchar(100) NOT NULL COMMENT '权限标识（如：system:user:add）',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父权限ID（0表示顶级）',
  `permission_type` tinyint NOT NULL DEFAULT 1 COMMENT '权限类型（1-菜单 2-按钮 3-API）',
  `component_address` varchar(255) NULL DEFAULT NULL COMMENT '组件地址',
  `router_address` varchar(255) NULL DEFAULT NULL COMMENT '路由地址',
  `sort` int NULL DEFAULT 0 COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1-启用 0-禁用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `icon` varchar(255) NULL DEFAULT NULL COMMENT 'icon图标',
  `link` varchar(255) NULL DEFAULT NULL COMMENT 'iframe 内嵌外部页面',
  `is_hide` tinyint NULL DEFAULT 0 COMMENT '是否在菜单中隐藏',
  `is_iframe` tinyint NULL DEFAULT 0 COMMENT '是否为iframe',
  `keep_alive` tinyint NULL DEFAULT 0 COMMENT '是否启用缓存',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_permission_code`(`permission_code`),
  INDEX `idx_parent_id`(`parent_id`)
) ENGINE = InnoDB COMMENT = '系统权限表';

-- ----------------------------
-- 角色权限关联表（全局）
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID；平台角色授权为空',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`role_id`, `permission_id`),
  INDEX `idx_permission_id`(`permission_id`),
  INDEX `idx_tenant_role`(`tenant_id`, `role_id`)
) ENGINE = InnoDB COMMENT = '角色权限关联表';

-- ----------------------------
-- 用户角色关联表（全局）
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID；平台用户授权为空',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`),
  INDEX `idx_role_id`(`role_id`),
  INDEX `idx_tenant_user`(`tenant_id`, `user_id`)
) ENGINE = InnoDB COMMENT = '用户角色关联表';

-- 用户可管理的门店范围，管理员角色通过应用规则决定是否需要逐店授权。
DROP TABLE IF EXISTS `sys_user_store`;
CREATE TABLE `sys_user_store` (
  `id` bigint NOT NULL COMMENT '授权ID（雪花ID）',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '默认门店：0否，1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_user_store`(`tenant_id`, `user_id`, `store_id`),
  INDEX `idx_tenant_user`(`tenant_id`, `user_id`),
  INDEX `idx_tenant_store`(`tenant_id`, `store_id`)
) ENGINE=InnoDB COMMENT='用户可管理门店关联表';

-- ----------------------------
-- 用户等级配置表（全局配置，不区分租户）
-- ----------------------------
DROP TABLE IF EXISTS `user_level`;
CREATE TABLE `user_level` (
  `id` bigint NOT NULL COMMENT '等级ID（雪花ID）',
  `level_code` int NOT NULL COMMENT '等级编码',
  `level_name` varchar(50) NOT NULL COMMENT '等级名称',
  `required_exp` int NOT NULL COMMENT '所需经验值',
  `icon` varchar(255) NULL DEFAULT NULL COMMENT '等级图标',
  `description` varchar(500) NULL DEFAULT NULL COMMENT '等级描述',
  `benefits` varchar(1000) NULL DEFAULT NULL COMMENT '等级权益',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用（1-启用，0-禁用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `sort` int NULL DEFAULT NULL COMMENT '显示顺序',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_level_code`(`level_code`)
) ENGINE = InnoDB COMMENT = '用户等级配置表';

-- ----------------------------
-- 用户积分记录表
-- ----------------------------
DROP TABLE IF EXISTS `user_points_record`;
CREATE TABLE `user_points_record` (
  `id` bigint NOT NULL COMMENT '记录ID（雪花ID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名（冗余快照）',
  `operation_type` tinyint NOT NULL COMMENT '积分操作类型：1-增加，2-减少',
  `source` tinyint NOT NULL COMMENT '积分来源：1-注册奖励，2-签到奖励，3-购物奖励，4-活动奖励，5-积分兑换，6-其它',
  `points` int NOT NULL COMMENT '积分变动数量',
  `before_points` int NOT NULL COMMENT '操作前积分',
  `after_points` int NOT NULL COMMENT '操作后积分',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注说明',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_user_time`(`user_id`, `operation_time`)
) ENGINE = InnoDB COMMENT = '用户积分记录表';

-- ----------------------------
-- 余额变动历史表
-- ----------------------------
DROP TABLE IF EXISTS `user_surplus_record`;
CREATE TABLE `user_surplus_record` (
  `id` bigint NOT NULL COMMENT '记录ID（雪花ID）',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `order_no` varchar(64) NULL DEFAULT NULL COMMENT '订单号',
  `amount` decimal(12, 2) NOT NULL COMMENT '充值余额',
  `new_balance` decimal(12, 2) NOT NULL COMMENT '变动后余额',
  `type` tinyint NOT NULL COMMENT '1-充值 2-消费 3-退款',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变动时间',
  `old_balance` decimal(12, 2) NOT NULL COMMENT '变动前余额',
  `operate_user_id` bigint NULL DEFAULT NULL COMMENT '操作用户id',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_order_no`(`order_no`),
  INDEX `idx_user_time`(`user_id`, `created_at`)
) ENGINE = InnoDB COMMENT = '余额变动历史表';

-- 超时余额支付的订单号栅栏；status=1 后迟到扣款必须被拒绝。
CREATE TABLE `balance_payment_fence` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_no` varchar(64) NOT NULL COMMENT '餐饮订单号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0允许扣款，1已封闭',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`tenant_id`, `order_no`)
) ENGINE=InnoDB COMMENT='余额支付订单号栅栏';
