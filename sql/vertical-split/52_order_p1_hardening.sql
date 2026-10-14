-- P1：下单幂等与取餐码并发安全。
ALTER TABLE `restaurant_order`
    ADD COLUMN IF NOT EXISTS `request_key` varchar(64) NULL COMMENT '租户内下单幂等键' AFTER `order_no`,
    ADD COLUMN IF NOT EXISTS `pickup_date` date NULL COMMENT '取餐码所属营业日期' AFTER `pickup_code`,
    ADD UNIQUE INDEX `uk_tenant_request_key` (`tenant_id`, `request_key`),
    ADD UNIQUE INDEX `uk_tenant_store_pickup` (`tenant_id`, `store_id`, `pickup_date`, `pickup_code`);

-- 存量订单没有请求键，保留 NULL；新代码始终写入非空幂等键。

CREATE TABLE IF NOT EXISTS `print_job` (
    `id` bigint NOT NULL COMMENT '打印任务ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `order_id` bigint NOT NULL COMMENT '业务订单ID',
    `store_id` bigint NOT NULL COMMENT '门店ID',
    `printer_id` bigint NOT NULL COMMENT '打印机ID',
    `printer_sn` varchar(64) NOT NULL COMMENT '打印机编号快照',
    `provider_order_id` varchar(128) NULL COMMENT '飞鹅云打印订单ID',
    `status` tinyint NOT NULL COMMENT '0已提交 1成功 2失败 3提交失败 4提交中',
    `attempts` int NOT NULL DEFAULT 1 COMMENT '提交次数',
    `last_error` varchar(500) NULL COMMENT '最后错误',
    `callback_time` datetime NULL COMMENT '最后回调时间',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_provider_order_id` (`provider_order_id`),
    INDEX `idx_tenant_order` (`tenant_id`, `order_id`),
    INDEX `idx_tenant_status_time` (`tenant_id`, `status`, `created_at`)
) ENGINE=InnoDB COMMENT='打印任务';
