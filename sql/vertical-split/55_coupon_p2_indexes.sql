-- P2：为租户内优惠券状态任务补充状态与时间复合索引。
ALTER TABLE `coupon`
    ADD INDEX `idx_tenant_status_end` (`tenant_id`, `status`, `end_time`);

ALTER TABLE `user_coupon`
    ADD INDEX `idx_tenant_status_expire` (`tenant_id`, `status`, `expire_time`),
    ADD INDEX `idx_tenant_status_start` (`tenant_id`, `status`, `start_time`);
