-- 存量优惠券库升级：显式券配对与长期有效用户券。
USE `liteshop_coupon`;

CREATE TABLE IF NOT EXISTS `coupon_stack_pair` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `coupon_id_low` bigint NOT NULL COMMENT '两张券中较小的券模板ID',
  `coupon_id_high` bigint NOT NULL COMMENT '两张券中较大的券模板ID',
  PRIMARY KEY (`tenant_id`, `coupon_id_low`, `coupon_id_high`),
  INDEX `idx_stack_high` (`tenant_id`, `coupon_id_high`)
) ENGINE = InnoDB COMMENT = '优惠券允许叠加配对表';

ALTER TABLE `coupon`
  MODIFY COLUMN `stackable` tinyint NULL DEFAULT 0 COMMENT '是否可叠加(0不可,1可；还需存在明确配对)';

ALTER TABLE `user_coupon`
  MODIFY COLUMN `expire_time` datetime NULL COMMENT '过期时间；空表示长期有效';
