-- 模板批量回收的任务记录；停止发券和建任务须同事务提交。
CREATE TABLE IF NOT EXISTS `coupon_revoke_batch` (
  `batch_id` varchar(36) NOT NULL COMMENT '回收任务ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `coupon_id` bigint NOT NULL COMMENT '模板ID',
  `operator_id` bigint NULL COMMENT '操作人ID',
  `reason` varchar(200) NOT NULL COMMENT '回收原因',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0处理中，1已完成',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_tenant_revoke_coupon` (`tenant_id`, `coupon_id`),
  KEY `idx_tenant_revoke_status` (`tenant_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板回收任务';
