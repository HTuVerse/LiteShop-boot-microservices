-- SaaS 套餐、订阅、加购、权益调整和用量计量（MySQL 8，可重复执行）
-- 部署顺序：先执行本脚本，再部署 user-service 和管理端。
SET NAMES utf8mb4;
USE liteshop_user;

CREATE TABLE IF NOT EXISTS saas_feature (
  id BIGINT NOT NULL COMMENT '雪花ID',
  feature_code VARCHAR(100) NOT NULL COMMENT '稳定功能编码，业务代码只引用此值',
  feature_name VARCHAR(100) NOT NULL,
  feature_type VARCHAR(16) NOT NULL COMMENT 'BOOLEAN/NUMBER/STRING/ENUM；QUOTA为旧版兼容值',
  unit VARCHAR(32) NULL COMMENT 'COUNT/BYTE/REQUEST/SEAT等',
  quota_mode VARCHAR(16) NULL COMMENT 'RESOURCE/PERIODIC/RATE/CONCURRENT',
  usage_source VARCHAR(16) NOT NULL DEFAULT 'COUNTER' COMMENT 'NONE/COUNTER/REALTIME',
  usage_metric VARCHAR(100) NULL COMMENT '实时用量提供器编码',
  default_value DECIMAL(24,6) NULL,
  default_text_value VARCHAR(500) NULL,
  allowed_values JSON NULL COMMENT 'ENUM 可选值 JSON 数组',
  description VARCHAR(500) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_feature_code (feature_code)
) ENGINE=InnoDB COMMENT='SaaS功能与配额目录';

CREATE TABLE IF NOT EXISTS saas_plan (
  id BIGINT NOT NULL,
  plan_code VARCHAR(100) NOT NULL,
  plan_name VARCHAR(100) NOT NULL,
  target_customer VARCHAR(200) NULL COMMENT '套餐适用客户',
  core_benefits VARCHAR(1000) NULL COMMENT '换行分隔的核心权益展示文案',
  description VARCHAR(500) NULL,
  recommended TINYINT NOT NULL DEFAULT 0 COMMENT '运营推荐套餐，同一时间最多一个',
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_plan_code (plan_code)
) ENGINE=InnoDB COMMENT='SaaS套餐主档';

CREATE TABLE IF NOT EXISTS saas_display_feature (
  id BIGINT NOT NULL,
  module_code VARCHAR(100) NOT NULL,
  module_name VARCHAR(100) NOT NULL,
  feature_code VARCHAR(100) NOT NULL,
  feature_name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_display_feature_code (feature_code),
  KEY idx_saas_display_feature_module (module_code,status,sort_order)
) ENGINE=InnoDB COMMENT='套餐对比页基础功能展示目录，不参与运行时鉴权';

CREATE TABLE IF NOT EXISTS saas_plan_version (
  id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  monthly_price DECIMAL(12,2) NULL,
  yearly_price DECIMAL(12,2) NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
  effective_from DATETIME NULL,
  effective_to DATETIME NULL,
  published_at DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_plan_version (plan_id,version_no), KEY idx_saas_plan_version_status (status)
) ENGINE=InnoDB COMMENT='不可变套餐版本';

CREATE TABLE IF NOT EXISTS saas_plan_entitlement (
  id BIGINT NOT NULL,
  plan_version_id BIGINT NOT NULL,
  feature_id BIGINT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  quota_value DECIMAL(24,6) NULL,
  text_value VARCHAR(500) NULL,
  unlimited TINYINT NOT NULL DEFAULT 0,
  reset_period VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/DAY/MONTH/BILLING_CYCLE',
  enforcement VARCHAR(8) NOT NULL DEFAULT 'HARD' COMMENT 'HARD/SOFT',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_plan_feature (plan_version_id,feature_id), KEY idx_saas_plan_ent_feature (feature_id)
) ENGINE=InnoDB COMMENT='套餐版本权益规则';

CREATE TABLE IF NOT EXISTS saas_addon (
  id BIGINT NOT NULL,
  addon_code VARCHAR(100) NOT NULL,
  addon_name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  monthly_price DECIMAL(12,2) NULL,
  yearly_price DECIMAL(12,2) NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  max_quantity INT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_addon_code (addon_code)
) ENGINE=InnoDB COMMENT='SaaS加购项';

CREATE TABLE IF NOT EXISTS saas_addon_entitlement (
  id BIGINT NOT NULL,
  addon_id BIGINT NOT NULL,
  feature_id BIGINT NOT NULL,
  operation VARCHAR(16) NOT NULL COMMENT 'ENABLE/ADD/SET',
  quota_value DECIMAL(24,6) NULL COMMENT '单份加购额度',
  text_value VARCHAR(500) NULL COMMENT 'STRING/ENUM 设置值',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_addon_feature (addon_id,feature_id), KEY idx_saas_addon_ent_feature (feature_id)
) ENGINE=InnoDB COMMENT='加购项权益规则';

CREATE TABLE IF NOT EXISTS saas_tenant_subscription (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  plan_version_id BIGINT NOT NULL,
  billing_cycle VARCHAR(16) NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY/YEARLY/CUSTOM',
  status VARCHAR(20) NOT NULL COMMENT 'TRIALING/ACTIVE/PAST_DUE/GRACE/SUSPENDED/CANCELED/EXPIRED',
  start_at DATETIME NOT NULL,
  current_period_start DATETIME NOT NULL,
  current_period_end DATETIME NOT NULL,
  trial_end_at DATETIME NULL,
  grace_end_at DATETIME NULL,
  cancel_at DATETIME NULL,
  cancel_at_period_end TINYINT NOT NULL DEFAULT 0,
  auto_renew TINYINT NOT NULL DEFAULT 0,
  external_subscription_id VARCHAR(128) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_external_subscription (external_subscription_id),
  KEY idx_saas_subscription_tenant_status (tenant_id,status,create_time), KEY idx_saas_subscription_version (plan_version_id)
) ENGINE=InnoDB COMMENT='租户套餐订阅';

CREATE TABLE IF NOT EXISTS saas_subscription_addon (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  subscription_id BIGINT NOT NULL,
  addon_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  start_at DATETIME NOT NULL,
  end_at DATETIME NULL,
  external_item_id VARCHAR(128) NULL,
  entitlement_snapshot JSON NOT NULL COMMENT '购买时权益快照，防止后续编辑影响存量客户',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_external_item (external_item_id),
  KEY idx_saas_sub_addon_tenant (tenant_id,subscription_id,status), KEY idx_saas_sub_addon_addon (addon_id)
) ENGINE=InnoDB COMMENT='租户订阅加购记录';

CREATE TABLE IF NOT EXISTS saas_tenant_entitlement_override (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  feature_id BIGINT NOT NULL,
  operation VARCHAR(16) NOT NULL COMMENT 'ENABLE/DISABLE/ADD/SET',
  quota_value DECIMAL(24,6) NULL,
  text_value VARCHAR(500) NULL,
  reason VARCHAR(500) NULL,
  effective_from DATETIME NULL,
  effective_to DATETIME NULL,
  created_by BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_saas_override_active (tenant_id,effective_from,effective_to), KEY idx_saas_override_feature (feature_id)
) ENGINE=InnoDB COMMENT='租户特殊权益调整审计记录';

CREATE TABLE IF NOT EXISTS saas_usage_counter (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  feature_code VARCHAR(100) NOT NULL,
  period_start DATETIME NOT NULL,
  period_end DATETIME NOT NULL,
  used_value DECIMAL(24,6) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_usage_period (tenant_id,feature_code,period_start),
  KEY idx_saas_usage_lookup (tenant_id,feature_code,period_end)
) ENGINE=InnoDB COMMENT='租户配额原子计数器';

CREATE TABLE IF NOT EXISTS saas_usage_event (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  feature_code VARCHAR(100) NOT NULL,
  amount DECIMAL(24,6) NOT NULL,
  event_type VARCHAR(16) NOT NULL COMMENT 'CONSUME/RELEASE',
  idempotency_key VARCHAR(128) NOT NULL,
  period_start DATETIME NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'PENDING/COMMITTED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_usage_idempotency (tenant_id,idempotency_key),
  KEY idx_saas_usage_event_feature (tenant_id,feature_code,create_time)
) ENGINE=InnoDB COMMENT='不可变用量事件与幂等凭据';

-- 平台菜单：仅创建缺失项，不覆盖管理员已经调整的菜单。
DROP PROCEDURE IF EXISTS migrate_saas_menu_20260907;
DELIMITER $$
CREATE PROCEDURE migrate_saas_menu_20260907()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_parent BIGINT DEFAULT NULL;
  DECLARE v_menu BIGINT DEFAULT NULL;
  IF NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='platform:saas') THEN
    SELECT id INTO v_parent FROM sys_permission WHERE permission_code='platform:menu' LIMIT 1;
    IF v_parent IS NULL THEN
      SELECT id INTO v_parent FROM sys_permission WHERE router_address='/platform' AND component_address='Layout' LIMIT 1;
    END IF;
    IF v_parent IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少平台管理目录，请先完成平台菜单迁移'; END IF;
    SELECT COALESCE(MAX(id),0)+1 INTO v_menu FROM sys_permission;
    INSERT INTO sys_permission
      (id,permission_name,permission_code,parent_id,permission_type,component_address,router_address,sort,status,icon,is_hide,is_iframe,keep_alive,menu_scope,create_time,update_time)
    VALUES
      (v_menu,'套餐与权益','platform:saas',v_parent,1,'platform/saas/index','/platform/saas',30,1,'ri:vip-diamond-line',0,0,0,1,NOW(),NOW());
    INSERT INTO sys_role_permission(tenant_id,role_id,permission_id)
      SELECT NULL,r.id,v_menu FROM sys_role r WHERE r.tenant_id IS NULL AND r.role_code='SUPER_ADMIN';
  END IF;
END$$
DELIMITER ;
CALL migrate_saas_menu_20260907();
DROP PROCEDURE IF EXISTS migrate_saas_menu_20260907;

SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name LIKE 'saas_%' ORDER BY table_name;
