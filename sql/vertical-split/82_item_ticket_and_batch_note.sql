-- 按菜品出单与按下单批次保留订单备注。
ALTER TABLE `liteshop_restaurant`.`print_template`
  ADD COLUMN `item_ticket_mode` tinyint NOT NULL DEFAULT 0 COMMENT '出单方式：0整批一张，1每个菜品明细一张' AFTER `print_copies`;

ALTER TABLE `liteshop_restaurant`.`restaurant_order_item`
  ADD COLUMN `batch_order_note` varchar(255) NULL COMMENT '本次下单的订单备注快照' AFTER `batch_no`;

-- 历史订单仅能恢复首次下单备注；既往加菜请求未保存备注。
UPDATE `liteshop_restaurant`.`restaurant_order_item` AS item
JOIN `liteshop_restaurant`.`restaurant_order` AS order_row ON order_row.`id` = item.`order_id`
  AND order_row.`tenant_id` = item.`tenant_id`
SET item.`batch_order_note` = order_row.`note`
WHERE COALESCE(item.`batch_no`, 1) = 1;
