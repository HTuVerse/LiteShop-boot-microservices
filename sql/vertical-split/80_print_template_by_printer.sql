-- 模板固定绑定打印机；下单批次决定打印菜品范围，不再限制模板用途。
ALTER TABLE `liteshop_restaurant`.`print_template`
  ADD COLUMN `show_order_total` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示订单合计：0-不显示，1-显示' AFTER `show_dish_amount`,
  DROP COLUMN `print_scene`;

-- 历史按用途设置的多个默认模板统一为一个，已有打印机绑定不受影响。
UPDATE `liteshop_restaurant`.`print_template` AS template
JOIN (
  SELECT `store_id`, MAX(`id`) AS keep_id
  FROM `liteshop_restaurant`.`print_template`
  WHERE `is_default` = 1
  GROUP BY `store_id`
) AS defaults ON template.`store_id` <=> defaults.`store_id`
SET template.`is_default` = 0
WHERE template.`is_default` = 1 AND template.`id` <> defaults.`keep_id`;
