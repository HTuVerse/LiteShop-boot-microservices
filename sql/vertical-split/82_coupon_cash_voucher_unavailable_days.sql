-- 代金券沿用券类型字段，新增不可用日期与每年自动匹配的节日。
USE `liteshop_coupon`;

ALTER TABLE `coupon`
  MODIFY COLUMN `type` tinyint NOT NULL DEFAULT 0 COMMENT '券类型：0满减，1整单折扣，2菜品免单，3菜品折扣，4代金券',
  ADD COLUMN `unavailable_dates` text NULL COMMENT '不可用的具体公历日期，yyyy-MM-dd，逗号分隔' AFTER `use_weekdays`,
  ADD COLUMN `unavailable_holidays` varchar(255) NULL COMMENT '每年不可用的节日编码，逗号分隔，仅节日当天' AFTER `unavailable_dates`;
