-- 菜品字段改为独立开关；升级时保留旧简约样式对规格和备注的隐藏效果。
ALTER TABLE `liteshop_restaurant`.`print_template`
  ADD COLUMN `show_dish_unit_price` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示菜品单价：0-不显示，1-显示' AFTER `show_dish_image`,
  ADD COLUMN `show_dish_quantity` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示菜品数量：0-不显示，1-显示' AFTER `show_dish_unit_price`,
  ADD COLUMN `show_dish_amount` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示菜品金额：0-不显示，1-显示' AFTER `show_dish_quantity`,
  ADD COLUMN `show_dish_original_price` tinyint NOT NULL DEFAULT 1 COMMENT '优惠菜品是否显示原价：0-不显示，1-显示' AFTER `show_dish_spec`,
  ADD COLUMN `show_dish_discount_price` tinyint NOT NULL DEFAULT 1 COMMENT '优惠菜品是否显示优惠价：0-不显示，1-显示' AFTER `show_dish_original_price`;

UPDATE `liteshop_restaurant`.`print_template`
SET `show_dish_spec` = 0, `show_dish_note` = 0
WHERE `dish_list_style` = 0 OR `dish_list_style` IS NULL;

ALTER TABLE `liteshop_restaurant`.`print_template`
  DROP COLUMN `dish_list_style`;
