USE `liteshop_restaurant`;

-- 加退菜统计必须依赖不可变事件；退菜会修改或删除订单项，不能事后从当前状态反推。
CREATE TABLE IF NOT EXISTS `order_dish_change_log` (
  `id` bigint NOT NULL COMMENT '事件ID',
  `event_key` varchar(80) NOT NULL COMMENT '租户内幂等事件键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint NULL COMMENT '订单项ID；订单项删除后仍保留',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID快照',
  `category_id` bigint NULL COMMENT '分类ID快照',
  `change_type` tinyint NOT NULL COMMENT '变更类型：1加菜，2退菜',
  `quantity` int NOT NULL COMMENT '变更数量，保存正数',
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '加菜成交金额或退菜金额',
  `reason` varchar(255) NULL COMMENT '变更原因',
  `occurred_at` datetime NOT NULL COMMENT '业务发生时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_event_key` (`tenant_id`,`event_key`),
  KEY `idx_change_analysis` (`tenant_id`,`store_id`,`occurred_at`,`change_type`,`dish_id`),
  KEY `idx_change_order` (`tenant_id`,`order_id`,`order_item_id`)
) ENGINE=InnoDB COMMENT='订单加退菜事件日志';

-- 可恢复仍存在订单项的历史加菜；历史退菜已经丢失原始事件，不能可靠回填。
INSERT INTO `order_dish_change_log`
  (`id`, `event_key`, `order_id`, `order_item_id`, `store_id`, `dish_id`, `category_id`,
   `change_type`, `quantity`, `amount`, `reason`, `occurred_at`, `tenant_id`)
SELECT roi.id,
       CONCAT('ADD:', roi.id),
       roi.order_id,
       roi.id,
       ro.store_id,
       roi.dish_id,
       roi.category_id,
       1,
       roi.quantity,
       COALESCE(roi.line_payable_amount, 0),
       '历史加菜回填',
       COALESCE(roi.add_time, roi.created_at, ro.created_at),
       ro.tenant_id
FROM `restaurant_order_item` roi
INNER JOIN `restaurant_order` ro ON roi.order_id = ro.id
WHERE roi.batch_no > 1
ON DUPLICATE KEY UPDATE `event_key` = VALUES(`event_key`);
