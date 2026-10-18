USE `liteshop_restaurant`;

-- 先执行此迁移，再部署会读写操作人字段的餐饮服务。
ALTER TABLE `restaurant_order`
  ADD COLUMN `operator_id` bigint NULL COMMENT '下单操作人ID，与会员ID分离' AFTER `user_id`,
  ADD COLUMN `operator_name` varchar(100) NULL COMMENT '下单操作人名称快照' AFTER `operator_id`;

ALTER TABLE `order_dish_change_log`
  ADD COLUMN `dish_name` varchar(100) NULL COMMENT '菜品名称快照' AFTER `dish_id`,
  ADD COLUMN `operator_id` bigint NULL COMMENT '加退菜操作人ID' AFTER `reason`,
  ADD COLUMN `operator_name` varchar(100) NULL COMMENT '加退菜操作人名称快照' AFTER `operator_id`;

ALTER TABLE `order_refund`
  ADD COLUMN `operator_id` bigint NULL COMMENT '发起退菜操作人ID' AFTER `reason`,
  ADD COLUMN `operator_name` varchar(100) NULL COMMENT '发起退菜操作人名称快照' AFTER `operator_id`;
