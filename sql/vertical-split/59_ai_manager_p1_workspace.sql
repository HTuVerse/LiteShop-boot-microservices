-- AI 店长 P1：经营顾问会话与建议反馈。
SET NAMES utf8mb4;
USE liteshop_user;

ALTER TABLE saas_ai_generation
  ADD COLUMN session_id BIGINT NULL COMMENT '经营顾问会话ID' AFTER quota_charged,
  ADD COLUMN input_text VARCHAR(1000) NULL COMMENT '用户问题或方案目标' AFTER session_id,
  ADD COLUMN reference_request_id VARCHAR(64) NULL COMMENT '关联诊断请求ID' AFTER input_text,
  ADD COLUMN feedback VARCHAR(20) NULL COMMENT 'USEFUL/NOT_USEFUL/IGNORED' AFTER reference_request_id,
  ADD COLUMN feedback_at DATETIME NULL COMMENT '建议反馈时间' AFTER feedback,
  ADD INDEX idx_ai_generation_session (tenant_id, session_id, created_at);

CREATE TABLE IF NOT EXISTS saas_ai_advisor_session (
  id BIGINT NOT NULL COMMENT '会话ID', tenant_id BIGINT NOT NULL COMMENT '租户ID',
  title VARCHAR(100) NOT NULL COMMENT '会话标题', scope_type VARCHAR(16) NOT NULL COMMENT 'TENANT/STORE',
  store_id BIGINT NULL COMMENT '门店ID', status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  diagnosis_request_id VARCHAR(64) NULL COMMENT '来源诊断请求ID', initial_request_id VARCHAR(64) NULL COMMENT '首条问题请求ID',
  created_by BIGINT NOT NULL COMMENT '发起用户',
  last_message_at DATETIME NOT NULL COMMENT '最近消息时间', created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  PRIMARY KEY (id), KEY idx_ai_session_user (tenant_id, created_by, status, last_message_at),
  KEY idx_ai_session_diagnosis (tenant_id, diagnosis_request_id),
  UNIQUE KEY uk_ai_session_initial_request (tenant_id, created_by, initial_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI经营顾问会话';

-- 细分权限仍兼容原 AI 店长菜单，升级时为已有角色继承相应操作权限。
SET @ai_manager := (SELECT id FROM sys_permission WHERE permission_code='operations:assistant' LIMIT 1);
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,component_address,router_address,
   sort,status,icon,is_hide,is_iframe,keep_alive,create_time,update_time)
VALUES
  (79,'使用经营顾问','operations:ai:advisor',@ai_manager,2,NULL,NULL,1,1,NULL,1,0,0,NOW(),NOW()),
  (81,'生成业务方案','operations:ai:plan:create',@ai_manager,2,NULL,NULL,3,1,NULL,1,0,0,NOW(),NOW()),
  (83,'查看经营记录','operations:ai:records',@ai_manager,2,NULL,NULL,5,1,NULL,1,0,0,NOW(),NOW()),
  (84,'生成经营诊断','operations:ai:diagnosis',@ai_manager,2,NULL,NULL,6,1,NULL,1,0,0,NOW(),NOW())
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),parent_id=VALUES(parent_id),status=1,update_time=NOW();

INSERT INTO sys_role_permission (tenant_id,role_id,permission_id)
SELECT source.tenant_id,source.role_id,target.id FROM sys_role_permission source
JOIN sys_permission target ON target.permission_code IN
  ('operations:ai:advisor','operations:ai:plan:create','operations:ai:records','operations:ai:diagnosis')
WHERE source.permission_id=@ai_manager
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id=source.role_id AND existing.permission_id=target.id);
