-- 独立餐桌预约表：支持同桌多时段、预约历史以及到店/完成状态追踪。
CREATE TABLE IF NOT EXISTS `table_reservation` (
  `id` bigint NOT NULL COMMENT '预约ID（雪花ID）',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `table_id` bigint NOT NULL COMMENT '桌位ID',
  `reservation_phone` varchar(20) NOT NULL COMMENT '预约手机号',
  `people_count` int NOT NULL DEFAULT 1 COMMENT '预约人数',
  `expected_arrival_time` datetime NOT NULL COMMENT '预计到店时间',
  `expected_end_time` datetime NOT NULL COMMENT '预计结束时间',
  `reservation_remark` varchar(200) NULL DEFAULT NULL COMMENT '预约备注',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待到店，1-已到店，2-已完成，3-已取消，4-爽约',
  `session_no` varchar(32) NULL DEFAULT NULL COMMENT '到店后关联的桌位会话编号',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '创建预约的操作人ID',
  `cancel_reason` varchar(200) NULL DEFAULT NULL COMMENT '取消原因',
  `arrived_at` datetime NULL DEFAULT NULL COMMENT '实际到店时间',
  `completed_at` datetime NULL DEFAULT NULL COMMENT '实际完成时间',
  `cancelled_at` datetime NULL DEFAULT NULL COMMENT '取消时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  INDEX `idx_reservation_table_time` (`tenant_id`, `table_id`, `status`, `expected_arrival_time`, `expected_end_time`),
  INDEX `idx_reservation_store_time` (`tenant_id`, `store_id`, `expected_arrival_time`),
  INDEX `idx_reservation_phone` (`tenant_id`, `reservation_phone`),
  INDEX `idx_reservation_session` (`tenant_id`, `session_no`)
) ENGINE = InnoDB COMMENT = '餐桌预约记录表';

-- 若某环境短暂使用过“一桌一条预留”版本，先迁移旧数据，再移除旧列。
SET @legacy_reservation_columns = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'table_info'
    AND COLUMN_NAME IN ('reservation_phone', 'expected_arrival_time', 'reservation_remark')
);
SET @migrate_legacy_reservations = IF(
  @legacy_reservation_columns = 3,
  'INSERT INTO table_reservation (id, store_id, table_id, reservation_phone, people_count, expected_arrival_time, expected_end_time, reservation_remark, status, created_at, updated_at, tenant_id) SELECT t.id, t.store_id, t.id, t.reservation_phone, GREATEST(t.seats, 1), t.expected_arrival_time, DATE_ADD(t.expected_arrival_time, INTERVAL 2 HOUR), t.reservation_remark, 0, t.created_at, NOW(), t.tenant_id FROM table_info t WHERE t.reservation_phone IS NOT NULL AND t.expected_arrival_time IS NOT NULL AND NOT EXISTS (SELECT 1 FROM table_reservation r WHERE r.tenant_id=t.tenant_id AND r.table_id=t.id AND r.reservation_phone=t.reservation_phone AND r.expected_arrival_time=t.expected_arrival_time)',
  'SELECT 1'
);
PREPARE migrate_legacy_stmt FROM @migrate_legacy_reservations;
EXECUTE migrate_legacy_stmt;
DEALLOCATE PREPARE migrate_legacy_stmt;

SET @has_legacy_reservation_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'table_info'
    AND INDEX_NAME = 'idx_table_expected_arrival'
);
SET @drop_legacy_reservation_index = IF(
  @has_legacy_reservation_index > 0,
  'DROP INDEX idx_table_expected_arrival ON table_info',
  'SELECT 1'
);
PREPARE drop_legacy_index_stmt FROM @drop_legacy_reservation_index;
EXECUTE drop_legacy_index_stmt;
DEALLOCATE PREPARE drop_legacy_index_stmt;

SET @drop_legacy_reservation_columns = IF(
  @legacy_reservation_columns = 3,
  'ALTER TABLE table_info DROP COLUMN reservation_phone, DROP COLUMN expected_arrival_time, DROP COLUMN reservation_remark',
  'SELECT 1'
);
PREPARE drop_legacy_columns_stmt FROM @drop_legacy_reservation_columns;
EXECUTE drop_legacy_columns_stmt;
DEALLOCATE PREPARE drop_legacy_columns_stmt;

-- 预约不再占用桌位状态；只有实际开桌才进入“使用中”。
UPDATE `table_info` SET `status` = 0, `updated_at` = NOW() WHERE `status` = 2;
