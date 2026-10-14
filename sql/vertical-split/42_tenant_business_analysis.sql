USE `liteshop_restaurant`;

ALTER TABLE `restaurant_order_item`
  ADD COLUMN `unit_cost_snapshot` decimal(10,2) NULL COMMENT '下单时门店实际单位成本快照；为空表示未维护成本' AFTER `unit_selling_price`,
  ADD COLUMN `line_cost_amount` decimal(12,2) NULL COMMENT '当前剩余数量对应的成交成本总额' AFTER `line_payable_amount`,
  ADD INDEX `idx_tenant_order_dish` (`tenant_id`, `order_id`, `dish_id`);

ALTER TABLE `restaurant_order`
  ADD INDEX `idx_tenant_payment_date_store` (`tenant_id`, `payment_time`, `store_id`, `payment_status`);

CREATE TABLE IF NOT EXISTS `analysis_store_daily` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `biz_date` date NOT NULL COMMENT '支付归属自然日',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `gross_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '退菜后、优惠前销售总额，单位：元',
  `net_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '优惠退款后的实收金额，单位：元',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额，单位：元',
  `valid_order_count` bigint NOT NULL DEFAULT 0 COMMENT '有效支付订单数',
  `sales_quantity` bigint NOT NULL DEFAULT 0 COMMENT '非赠品净销量',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总更新时间',
  PRIMARY KEY (`tenant_id`, `biz_date`, `store_id`),
  INDEX `idx_analysis_store_date` (`tenant_id`, `store_id`, `biz_date`)
) ENGINE=InnoDB COMMENT='租户经营分析门店日汇总';

CREATE TABLE IF NOT EXISTS `analysis_store_dish_daily` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `biz_date` date NOT NULL COMMENT '支付归属自然日',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `category_id` bigint NOT NULL DEFAULT 0 COMMENT '成交时分类ID；0表示未分类',
  `sales_quantity` bigint NOT NULL DEFAULT 0 COMMENT '非赠品净销量',
  `gross_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '非赠品优惠前销售额，单位：元',
  `net_sales_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '订单项优惠退款后金额，单位：元',
  `cost_amount` decimal(18,2) NULL COMMENT '成交成本总额；成本不完整时仅用于内部核对',
  `gross_profit_amount` decimal(18,2) NULL COMMENT '毛利额；存在未知成本时为空',
  `order_count` bigint NOT NULL DEFAULT 0 COMMENT '包含该菜品的去重订单数',
  `gift_quantity` bigint NOT NULL DEFAULT 0 COMMENT '赠送数量',
  `unknown_cost_quantity` bigint NOT NULL DEFAULT 0 COMMENT '未维护成交成本的数量',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总更新时间',
  PRIMARY KEY (`tenant_id`, `biz_date`, `store_id`, `dish_id`, `category_id`),
  INDEX `idx_analysis_dish_date` (`tenant_id`, `dish_id`, `biz_date`, `store_id`),
  INDEX `idx_analysis_store_dish_date` (`tenant_id`, `store_id`, `dish_id`, `biz_date`)
) ENGINE=InnoDB COMMENT='租户经营分析门店菜品日汇总';

-- 历史订单没有可靠的成交成本快照，只回填销售指标；毛利成本完整度会明确显示为0。
INSERT INTO `analysis_store_daily`
  (`tenant_id`, `biz_date`, `store_id`, `gross_sales_amount`, `net_sales_amount`,
   `discount_amount`, `valid_order_count`, `sales_quantity`, `updated_at`)
SELECT ro.tenant_id, DATE(ro.payment_time), ro.store_id,
       COALESCE(SUM(ro.total_amount), 0), COALESCE(SUM(ro.actual_amount), 0),
       COALESCE(SUM(ro.discount_amount), 0), COUNT(*),
       COALESCE(SUM(items.sales_quantity), 0), NOW()
FROM restaurant_order ro
LEFT JOIN (
  SELECT tenant_id, order_id,
         SUM(CASE WHEN COALESCE(is_gift, 0) = 0 THEN quantity ELSE 0 END) AS sales_quantity
  FROM restaurant_order_item GROUP BY tenant_id, order_id
) items ON items.tenant_id = ro.tenant_id AND items.order_id = ro.id
WHERE ro.payment_status IN (1, 2) AND ro.status != 6 AND ro.payment_time IS NOT NULL
GROUP BY ro.tenant_id, DATE(ro.payment_time), ro.store_id
ON DUPLICATE KEY UPDATE
  gross_sales_amount = VALUES(gross_sales_amount), net_sales_amount = VALUES(net_sales_amount),
  discount_amount = VALUES(discount_amount), valid_order_count = VALUES(valid_order_count),
  sales_quantity = VALUES(sales_quantity), updated_at = NOW();

INSERT INTO `analysis_store_dish_daily`
  (`tenant_id`, `biz_date`, `store_id`, `dish_id`, `category_id`, `sales_quantity`,
   `gross_sales_amount`, `net_sales_amount`, `cost_amount`, `gross_profit_amount`,
   `order_count`, `gift_quantity`, `unknown_cost_quantity`, `updated_at`)
SELECT ro.tenant_id, DATE(ro.payment_time), ro.store_id, roi.dish_id, COALESCE(roi.category_id, 0),
       SUM(CASE WHEN COALESCE(roi.is_gift, 0) = 0 THEN roi.quantity ELSE 0 END),
       SUM(CASE WHEN COALESCE(roi.is_gift, 0) = 0 THEN roi.unit_list_price * roi.quantity ELSE 0 END),
       SUM(CASE WHEN COALESCE(roi.is_gift, 0) = 0 THEN roi.line_payable_amount ELSE 0 END),
       NULL, NULL,
       COUNT(DISTINCT CASE WHEN COALESCE(roi.is_gift, 0) = 0 THEN ro.id END),
       SUM(CASE WHEN roi.is_gift = 1 THEN roi.quantity ELSE 0 END),
       SUM(roi.quantity), NOW()
FROM restaurant_order ro
INNER JOIN restaurant_order_item roi ON roi.tenant_id = ro.tenant_id AND roi.order_id = ro.id
WHERE ro.payment_status IN (1, 2) AND ro.status != 6 AND ro.payment_time IS NOT NULL
GROUP BY ro.tenant_id, DATE(ro.payment_time), ro.store_id, roi.dish_id, COALESCE(roi.category_id, 0)
ON DUPLICATE KEY UPDATE
  sales_quantity = VALUES(sales_quantity), gross_sales_amount = VALUES(gross_sales_amount),
  net_sales_amount = VALUES(net_sales_amount), cost_amount = VALUES(cost_amount),
  gross_profit_amount = VALUES(gross_profit_amount), order_count = VALUES(order_count),
  gift_quantity = VALUES(gift_quantity), unknown_cost_quantity = VALUES(unknown_cost_quantity),
  updated_at = NOW();
