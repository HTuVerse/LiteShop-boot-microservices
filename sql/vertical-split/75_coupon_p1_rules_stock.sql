USE `liteshop_coupon`;

-- 存量券默认全部门店、每天全天可用。
ALTER TABLE `coupon`
  MODIFY COLUMN `type` tinyint NOT NULL DEFAULT 0 COMMENT '类型：0满减，1整单折扣，2菜品免单，3菜品折扣',
  ADD COLUMN `store_ids` varchar(2000) NULL COMMENT '适用门店ID，逗号分隔；空表示全部门店' AFTER `min_amount`,
  ADD COLUMN `use_weekdays` varchar(13) NULL COMMENT '可用星期，1周一至7周日，逗号分隔；空表示每天' AFTER `store_ids`,
  ADD COLUMN `use_start_time` time NULL COMMENT '每日可用开始时间；空表示全天' AFTER `use_weekdays`,
  ADD COLUMN `use_end_time` time NULL COMMENT '每日可用结束时间；空表示全天' AFTER `use_start_time`;

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
