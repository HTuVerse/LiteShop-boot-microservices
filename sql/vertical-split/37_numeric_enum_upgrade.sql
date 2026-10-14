-- 餐饮域字符串枚举改为数值编码
-- 适用 MySQL 5.7 / 8.0。请在 liteshop_restaurant 数据库执行。
-- 已使用新版 02_liteshop_restaurant.sql 初始化的空库不需要执行本脚本。

SET @schema_name = DATABASE();

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='pricing_campaign' AND column_name='campaign_type') IN ('varchar','char'),
  'UPDATE `pricing_campaign` SET `campaign_type`=CASE `campaign_type` WHEN ''PROMOTION'' THEN 0 WHEN ''QUANTITY_DISCOUNT'' THEN 1 WHEN ''ORDER_DISCOUNT'' THEN 2 ELSE `campaign_type` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='pricing_campaign' AND column_name='stack_mode') IN ('varchar','char'),
  'UPDATE `pricing_campaign` SET `stack_mode`=CASE `stack_mode` WHEN ''EXCLUSIVE'' THEN 0 WHEN ''STACKABLE'' THEN 1 ELSE `stack_mode` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `pricing_campaign`
  MODIFY COLUMN `campaign_type` tinyint NOT NULL COMMENT '活动类型：0直接促销，1数量优惠，2订单优惠',
  MODIFY COLUMN `stack_mode` tinyint NOT NULL DEFAULT 0 COMMENT '叠加方式：0互斥择优，1允许叠加';

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='pricing_rule' AND column_name='condition_type') IN ('varchar','char'),
  'UPDATE `pricing_rule` SET `condition_type`=CASE `condition_type` WHEN ''ALWAYS'' THEN 0 WHEN ''QUANTITY'' THEN 1 WHEN ''ORDER_AMOUNT'' THEN 2 ELSE `condition_type` END, `grouping_type`=CASE `grouping_type` WHEN ''SAME_DISH'' THEN 0 WHEN ''CATEGORY'' THEN 1 WHEN ''ANY'' THEN 2 ELSE `grouping_type` END, `action_type`=CASE `action_type` WHEN ''FIXED_PRICE'' THEN 0 WHEN ''DISCOUNT_RATE'' THEN 1 WHEN ''REDUCE_AMOUNT'' THEN 2 WHEN ''FREE_ITEM'' THEN 3 ELSE `action_type` END, `repeat_mode`=CASE `repeat_mode` WHEN ''ONCE'' THEN 0 WHEN ''EVERY_GROUP'' THEN 1 ELSE `repeat_mode` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `pricing_rule`
  MODIFY COLUMN `condition_type` tinyint NOT NULL COMMENT '条件类型：0始终生效，1数量条件，2订单金额条件',
  MODIFY COLUMN `grouping_type` tinyint NULL COMMENT '分组类型：0同菜品，1分类，2任意商品',
  MODIFY COLUMN `action_type` tinyint NOT NULL COMMENT '优惠动作：0固定价，1折扣率，2直减，3赠送',
  MODIFY COLUMN `repeat_mode` tinyint NOT NULL DEFAULT 0 COMMENT '重复方式：0仅一次，1每组重复';

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='pricing_campaign_scope' AND column_name='target_type') IN ('varchar','char'),
  'UPDATE `pricing_campaign_scope` SET `target_type`=CASE `target_type` WHEN ''ALL'' THEN 0 WHEN ''DISH'' THEN 1 WHEN ''CATEGORY'' THEN 2 ELSE `target_type` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `pricing_campaign_scope`
  MODIFY COLUMN `target_type` tinyint NOT NULL COMMENT '目标类型：0全部，1菜品，2分类';

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='pricing_campaign_audience' AND column_name='audience_type') IN ('varchar','char'),
  'UPDATE `pricing_campaign_audience` SET `audience_type`=CASE `audience_type` WHEN ''ALL'' THEN 0 WHEN ''MEMBER'' THEN 1 WHEN ''MEMBER_LEVEL'' THEN 2 ELSE `audience_type` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `pricing_campaign_audience`
  MODIFY COLUMN `audience_type` tinyint NOT NULL COMMENT '人群类型：0全部，1会员，2指定会员等级',
  MODIFY COLUMN `audience_value` int NULL COMMENT '指定会员等级时保存等级编码';

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='order_price_adjustment' AND column_name='adjustment_type') IN ('varchar','char'),
  'UPDATE `order_price_adjustment` SET `adjustment_type`=CASE `adjustment_type` WHEN ''MEMBER_PRICE'' THEN 0 WHEN ''PROMOTION'' THEN 1 WHEN ''QUANTITY_DISCOUNT'' THEN 2 WHEN ''NTH_ITEM_DISCOUNT'' THEN 2 WHEN ''ORDER_DISCOUNT'' THEN 3 WHEN ''GIFT'' THEN 4 WHEN ''MANUAL_DISCOUNT'' THEN 5 ELSE `adjustment_type` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `order_price_adjustment`
  MODIFY COLUMN `adjustment_type` tinyint NOT NULL COMMENT '调整类型：0会员价，1直接促销，2第N件优惠，3订单优惠，4赠品，5人工折扣';

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='menu_change_record' AND column_name='change_type') IN ('varchar','char'),
  'UPDATE `menu_change_record` SET `change_type`=CASE `change_type` WHEN ''STORE_CONFIG'' THEN 0 ELSE `change_type` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `menu_change_record`
  MODIFY COLUMN `change_type` tinyint NOT NULL COMMENT '变更类型：0门店菜品配置';

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='print_template' AND column_name='paper_width') IN ('varchar','char'),
  'UPDATE `print_template` SET `paper_width`=CASE `paper_width` WHEN ''58mm'' THEN 0 WHEN ''80mm'' THEN 1 ELSE `paper_width` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `print_template`
  MODIFY COLUMN `paper_width` tinyint NOT NULL DEFAULT 0 COMMENT '纸张宽度：0为58mm，1为80mm';

SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='printer_info' AND column_name='printer_brand') IN ('varchar','char'),
  'UPDATE `printer_info` SET `printer_brand`=CASE `printer_brand` WHEN ''FEIER'' THEN 1 WHEN ''飞鹅'' THEN 1 WHEN ''OTHER'' THEN 2 WHEN ''其它'' THEN 2 WHEN ''其他'' THEN 2 ELSE `printer_brand` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `printer_info`
  MODIFY COLUMN `printer_brand` tinyint NULL COMMENT '打印机品牌：1飞鹅，2其他';
