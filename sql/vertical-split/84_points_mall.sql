-- 积分兑换商城：独立商品/订单库，并扩展现有积分与优惠券幂等记录。
CREATE DATABASE IF NOT EXISTS `liteshop_mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `liteshop_mall`;

-- 租户维护的兑换商品；本期全部指向优惠券模板。
CREATE TABLE IF NOT EXISTS `mall_product` (
  `id` bigint NOT NULL COMMENT '商品ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `name` varchar(80) NOT NULL COMMENT '商品名称',
  `description` varchar(500) NULL COMMENT '商品说明',
  `image_url` varchar(500) NULL COMMENT '商品图片地址',
  `coupon_id` bigint NOT NULL COMMENT '优惠券模板ID',
  `points_price` int NOT NULL COMMENT '所需积分，必须大于零',
  `stock` int NOT NULL COMMENT '可兑换库存',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0下架，1上架',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status_sort` (`tenant_id`, `status`, `sort`),
  CONSTRAINT `chk_mall_product_price` CHECK (`points_price` > 0),
  CONSTRAINT `chk_mall_product_stock` CHECK (`stock` >= 0)
) ENGINE=InnoDB COMMENT='租户积分兑换商品';

-- 订单保留商品和券的快照；请求键阻止重复点击形成多张订单。
CREATE TABLE IF NOT EXISTS `mall_exchange_order` (
  `id` bigint NOT NULL COMMENT '兑换订单ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '兑换会员ID',
  `product_id` bigint NOT NULL COMMENT '兑换商品ID',
  `product_name` varchar(80) NOT NULL COMMENT '兑换时商品名称',
  `coupon_id` bigint NOT NULL COMMENT '兑换时优惠券模板ID',
  `points_cost` int NOT NULL COMMENT '兑换时积分价格',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SPENT/REFUNDING/SUCCESS/FAILED',
  `request_key` varchar(64) NOT NULL COMMENT '客户端幂等键',
  `user_coupon_id` bigint NULL COMMENT '发放后的用户券ID',
  `failure_reason` varchar(200) NULL COMMENT '失败原因',
  `processing_until` datetime NULL COMMENT '处理租约截止时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user_request` (`tenant_id`, `user_id`, `request_key`),
  KEY `idx_tenant_user_time` (`tenant_id`, `user_id`, `created_at`),
  KEY `idx_retry` (`status`, `processing_until`)
) ENGINE=InnoDB COMMENT='积分兑换订单';

USE `liteshop_user`;
-- 积分账务接口以 tenant_id + 幂等键确保重试只记一次。
ALTER TABLE `user_points_record`
  ADD COLUMN IF NOT EXISTS `idempotency_key` varchar(128) NULL COMMENT '业务幂等键';
-- 兼容已运行 56 号迁移的环境，同时保证新库也有唯一约束。
SELECT COUNT(*) INTO @mall_points_index_exists FROM information_schema.statistics
WHERE table_schema='liteshop_user' AND table_name='user_points_record' AND index_name='uk_points_idempotency';
SET @mall_points_index_sql = IF(@mall_points_index_exists=0,
  'ALTER TABLE user_points_record ADD UNIQUE INDEX uk_points_idempotency (tenant_id,idempotency_key)', 'SELECT 1');
PREPARE mall_points_index_stmt FROM @mall_points_index_sql;
EXECUTE mall_points_index_stmt;
DEALLOCATE PREPARE mall_points_index_stmt;

-- 租户后台新增菜单，继承已有优惠券管理角色的授权。
INSERT INTO `sys_permission` (`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,
 `component_address`,`router_address`,`sort`,`status`,`icon`,`is_hide`,`is_iframe`,`keep_alive`)
SELECT 900119,'积分兑换管理','marketing:points-mall',p.id,1,
 'activity/pointsMall/index','/marketing/points-mall',43,1,'Present',0,0,1
FROM `sys_permission` p
WHERE p.permission_code='marketing:menu'
  AND NOT EXISTS (SELECT 1 FROM `sys_permission` x WHERE x.permission_code='marketing:points-mall');

INSERT INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT DISTINCT rp.tenant_id,rp.role_id,m.id
FROM `sys_role_permission` rp
JOIN `sys_permission` c ON c.id=rp.permission_id AND c.permission_code='marketing:coupon'
JOIN `sys_permission` m ON m.permission_code='marketing:points-mall'
WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` x
  WHERE x.tenant_id <=> rp.tenant_id AND x.role_id=rp.role_id AND x.permission_id=m.id);

USE `liteshop_coupon`;
-- 通过来源订单唯一索引让重试返回已发券，普通发券仍允许空值。
ALTER TABLE `user_coupon`
  ADD COLUMN IF NOT EXISTS `source_order_id` bigint NULL COMMENT '积分兑换订单ID';
ALTER TABLE `user_coupon`
  ADD UNIQUE INDEX `uk_tenant_source_order` (`tenant_id`,`source_order_id`);
