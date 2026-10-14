-- P1：优惠券核销绑定订单，MQ 消费具备数据库幂等凭据。
ALTER TABLE `verify_record`
    ADD COLUMN IF NOT EXISTS `order_id` bigint NULL COMMENT '订单核销来源，人工核销为空' AFTER `user_coupon_id`,
    ADD UNIQUE INDEX `uk_tenant_coupon_order` (`tenant_id`, `user_coupon_id`, `order_id`);

CREATE TABLE IF NOT EXISTS `coupon_message_receipt` (
    `event_id` varchar(64) NOT NULL COMMENT '业务事件唯一键',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`event_id`),
    INDEX `idx_tenant_created` (`tenant_id`, `created_at`)
) ENGINE=InnoDB COMMENT='优惠券消息消费幂等凭据';
