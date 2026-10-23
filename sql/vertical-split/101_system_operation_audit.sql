-- 系统操作审计日志。先在各业务库创建投递箱，再启动审计采集代码。
-- 平台范围使用 tenant_scope_id=0；日志本身不参与 MyBatis 租户 SQL 改写。
USE `liteshop_user`;

CREATE TABLE IF NOT EXISTS `sys_operation_audit` (
  `id` bigint NOT NULL COMMENT '审计日志雪花ID',
  `event_id` varchar(36) NOT NULL COMMENT '跨服务幂等事件ID',
  `tenant_scope_id` bigint NOT NULL COMMENT '查询归属租户ID；0表示平台全局',
  `actor_tenant_id` bigint NULL COMMENT '操作账号所属租户ID；平台账号为空',
  `scope_tenant_id` bigint NULL COMMENT '操作时会话所在租户ID',
  `store_id` bigint NULL COMMENT '操作时所在门店ID',
  `actor_user_id` bigint NULL COMMENT '操作账号ID；登录失败时可为空',
  `actor_account` varchar(100) NOT NULL COMMENT '操作账号快照或尝试登录的账号',
  `occurred_at` datetime(3) NOT NULL COMMENT '操作发生时间',
  `received_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计服务接收时间',
  `client_ip` varchar(45) NOT NULL COMMENT '按可信代理规则解析的客户端IP',
  `ip_location` varchar(200) NOT NULL COMMENT '操作时IP归属地快照',
  `geo_database_version` varchar(40) NULL COMMENT '归属地数据库版本',
  `service_name` varchar(80) NOT NULL COMMENT '操作来源微服务',
  `module_code` varchar(80) NOT NULL COMMENT '操作模块编码',
  `operation_type` varchar(40) NOT NULL COMMENT '操作类型，如LOGIN、CREATE、UPDATE、DELETE',
  `object_type` varchar(80) NULL COMMENT '操作对象类型',
  `object_id` varchar(100) NULL COMMENT '操作对象ID',
  `summary` varchar(500) NOT NULL COMMENT '脱敏后的操作摘要',
  `result` varchar(20) NOT NULL COMMENT '执行结果：SUCCESS、FAILURE、DENIED',
  `result_message` varchar(500) NULL COMMENT '脱敏后的失败原因',
  `request_id` varchar(36) NOT NULL COMMENT '关联同一请求的ID',
  `request_path` varchar(300) NOT NULL COMMENT '请求路径，不含查询参数',
  `diff_json` json NULL COMMENT '重要数据修改前后字段差异，敏感字段不得写入',
  `canonical_payload` longtext NOT NULL COMMENT '参与HMAC计算的规范化原始事件快照',
  `chain_sequence` bigint NOT NULL COMMENT '归属范围内的校验链序号',
  `previous_hash` char(64) NOT NULL COMMENT '上一条日志的HMAC摘要',
  `entry_hash` char(64) NOT NULL COMMENT '本条日志的HMAC摘要',
  `key_version` varchar(30) NOT NULL COMMENT 'HMAC密钥版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_event_id` (`event_id`),
  UNIQUE KEY `uk_audit_scope_sequence` (`tenant_scope_id`, `chain_sequence`),
  KEY `idx_audit_scope_time` (`tenant_scope_id`, `occurred_at`, `id`),
  KEY `idx_audit_scope_actor_time` (`tenant_scope_id`, `actor_user_id`, `occurred_at`),
  KEY `idx_audit_scope_module_time` (`tenant_scope_id`, `module_code`, `occurred_at`),
  KEY `idx_audit_ip_time` (`client_ip`, `occurred_at`)
) ENGINE=InnoDB COMMENT='系统操作审计日志，应用层只允许追加';

CREATE TABLE IF NOT EXISTS `sys_operation_audit_chain` (
  `tenant_scope_id` bigint NOT NULL COMMENT '校验链所属租户ID；0表示平台全局',
  `last_sequence` bigint NOT NULL DEFAULT 0 COMMENT '最近一次写入的链序号',
  `last_hash` char(64) NOT NULL DEFAULT '' COMMENT '最近一次写入的HMAC摘要',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后写入时间',
  PRIMARY KEY (`tenant_scope_id`)
) ENGINE=InnoDB COMMENT='系统审计日志校验链游标';

-- 全局菜单定义；实际角色授权仍由运营人员按职责分配。
INSERT INTO `sys_permission`
  (`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,`component_address`,`router_address`,`menu_scope`,`sort`,`status`,`icon`,`is_hide`,`is_iframe`,`keep_alive`,`create_time`,`update_time`)
SELECT 202610240101,'操作审计日志','system:audit',0,1,'system/audit/index','/system/audit',0,26,1,'ri:shield-check-line',0,0,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_code`='system:audit');

INSERT INTO `sys_permission`
  (`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,`sort`,`status`,`create_time`,`update_time`)
SELECT 202610240102,'查看审计详情','system:audit:detail',p.id,2,1,1,NOW(),NOW()
FROM `sys_permission` p WHERE p.permission_code='system:audit'
  AND NOT EXISTS (SELECT 1 FROM `sys_permission` x WHERE x.permission_code='system:audit:detail');

INSERT INTO `sys_permission`
  (`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,`sort`,`status`,`create_time`,`update_time`)
SELECT 202610240103,'导出审计日志','system:audit:export',p.id,2,2,1,NOW(),NOW()
FROM `sys_permission` p WHERE p.permission_code='system:audit'
  AND NOT EXISTS (SELECT 1 FROM `sys_permission` x WHERE x.permission_code='system:audit:export');

-- 已有商户 ADMIN 默认可查询和查看详情；导出需单独授权。
INSERT IGNORE INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT r.tenant_id,r.id,p.id FROM `sys_role` r
JOIN `sys_permission` p ON p.permission_code IN ('system:audit','system:audit:detail')
WHERE r.role_code='ADMIN' AND r.tenant_id IS NOT NULL;

INSERT IGNORE INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT NULL,r.id,p.id FROM `sys_role` r
JOIN `sys_permission` p ON p.permission_code IN ('system:audit','system:audit:detail','system:audit:export')
WHERE r.role_code='SUPER_ADMIN' AND r.tenant_id IS NULL;
