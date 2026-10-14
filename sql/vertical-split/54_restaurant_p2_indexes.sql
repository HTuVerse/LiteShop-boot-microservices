-- P2：为租户内桌台、会话和订单列表补充与实际查询条件一致的复合索引。
ALTER TABLE `table_info`
    ADD INDEX `idx_tenant_store_status_created` (`tenant_id`, `store_id`, `status`, `created_at`);

ALTER TABLE `table_session`
    ADD INDEX `idx_tenant_table_status_time` (`tenant_id`, `table_id`, `status`, `created_at`),
    ADD INDEX `idx_tenant_store_status_start` (`tenant_id`, `store_id`, `status`, `start_time`);

ALTER TABLE `restaurant_order`
    ADD INDEX `idx_tenant_session_created` (`tenant_id`, `session_no`, `created_at`);
