-- P1 剩余缺口：桌台扫码票据可吊销。
ALTER TABLE `table_info`
    ADD COLUMN IF NOT EXISTS `scan_token_id` varchar(32) NULL COMMENT '扫码票据编号，轮换后旧二维码立即失效' AFTER `table_name`;

-- 餐厅库：跨服务写操作发件箱。每个服务库都要有雪花租约表。
CREATE TABLE IF NOT EXISTS `local_outbox` (
    `id` bigint NOT NULL COMMENT '事件行ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `event_id` varchar(128) NOT NULL COMMENT '业务幂等键',
    `event_type` varchar(32) NOT NULL COMMENT 'COUPON_VERIFY、COUPON_RESTORE、BALANCE_CHANGE',
    `payload` text NOT NULL COMMENT '补偿所需载荷',
    `status` varchar(16) NOT NULL COMMENT 'PENDING、SENT、UNKNOWN、ROLLBACK、DONE、COMPENSATED、NEEDS_REVIEW',
    `committed` tinyint NOT NULL DEFAULT 0 COMMENT '外层业务事务是否已提交：0否，1是',
    `attempts` int NOT NULL DEFAULT 0 COMMENT '尝试次数',
    `last_error` varchar(500) NULL COMMENT '最后错误',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_local_outbox_event` (`tenant_id`, `event_id`),
    KEY `idx_local_outbox_reconcile` (`status`, `updated_at`)
) ENGINE=InnoDB COMMENT='餐厅跨服务发件箱';

ALTER TABLE `print_job`
    ADD COLUMN IF NOT EXISTS `job_key` varchar(128) NULL COMMENT '打印机+订单+票据类型幂等键' AFTER `printer_sn`,
    ADD COLUMN IF NOT EXISTS `content` mediumtext NULL COMMENT '待发送小票内容' AFTER `job_key`;
ALTER TABLE `print_job`
    ADD UNIQUE INDEX `uk_print_job_key` (`tenant_id`, `job_key`);

-- 抽奖库。
CREATE TABLE IF NOT EXISTS `lottery_prize_outbox` (
    `id` bigint NOT NULL COMMENT '发奖事件ID',
    `tenant_id` bigint NOT NULL COMMENT '租户ID',
    `event_id` varchar(128) NOT NULL COMMENT '发奖幂等键',
    `record_id` bigint NOT NULL COMMENT '中奖记录ID',
    `prize_type` varchar(16) NOT NULL COMMENT 'COUPON 或 POINTS',
    `payload` text NOT NULL COMMENT '发奖载荷',
    `status` varchar(16) NOT NULL COMMENT 'PENDING、SENT、DONE、FAILED',
    `attempts` int NOT NULL DEFAULT 0 COMMENT '投递次数',
    `last_error` varchar(500) NULL COMMENT '最后错误',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lottery_prize_event` (`event_id`)
) ENGINE=InnoDB COMMENT='抽奖发奖发件箱';

-- 用户库。
ALTER TABLE `sys_user`
    ADD COLUMN IF NOT EXISTS `totp_secret` varchar(64) NULL COMMENT '管理员动态口令密钥，空表示未启用' AFTER `open_id`,
    ADD COLUMN IF NOT EXISTS `must_change_password` tinyint NOT NULL DEFAULT 0 COMMENT '1表示必须修改一次性密码' AFTER `totp_secret`;
ALTER TABLE `user_points_record`
    ADD COLUMN IF NOT EXISTS `idempotency_key` varchar(128) NULL COMMENT '积分入账幂等键' AFTER `remark`;
ALTER TABLE `user_points_record`
    ADD UNIQUE INDEX `uk_points_idempotency` (`tenant_id`, `idempotency_key`);

-- 每个服务自己的库都执行。启动时也会自动建表。
CREATE TABLE IF NOT EXISTS `snowflake_worker_lease` (
    `worker_id` int NOT NULL COMMENT '雪花 worker，0-31',
    `datacenter_id` int NOT NULL COMMENT '雪花 datacenter，0-31',
    `instance_id` varchar(128) NOT NULL COMMENT '持有实例',
    `expires_at` datetime NOT NULL COMMENT '租约过期时间',
    PRIMARY KEY (`worker_id`, `datacenter_id`)
) ENGINE=InnoDB COMMENT='雪花节点租约';
