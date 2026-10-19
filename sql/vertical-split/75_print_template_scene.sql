-- 现有模板继续用于提交订单；门店可另建加菜专用模板。
ALTER TABLE `liteshop_restaurant`.`print_template`
  ADD COLUMN `print_scene` varchar(20) NOT NULL DEFAULT 'ORDER'
  COMMENT '打印用途：ORDER提交订单，ADD_ITEMS加菜' AFTER `template_name`;
