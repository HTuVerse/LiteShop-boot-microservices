-- SaaS 商业化能力增量升级（MySQL 8，可重复执行）
-- 前置条件：已执行 22_saas_entitlements.sql。执行前请备份 liteshop_user。
SET NAMES utf8mb4;
USE liteshop_user;

DROP PROCEDURE IF EXISTS migrate_saas_commercial_20260907;
DELIMITER $$
CREATE PROCEDURE migrate_saas_commercial_20260907()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_feature' AND column_name='default_text_value') THEN
    ALTER TABLE saas_feature ADD COLUMN default_text_value VARCHAR(500) NULL AFTER default_value;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_feature' AND column_name='allowed_values') THEN
    ALTER TABLE saas_feature ADD COLUMN allowed_values JSON NULL AFTER default_text_value;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_feature' AND column_name='usage_source') THEN
    ALTER TABLE saas_feature ADD COLUMN usage_source VARCHAR(16) NOT NULL DEFAULT 'COUNTER' COMMENT 'NONE/COUNTER/REALTIME' AFTER quota_mode;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_feature' AND column_name='usage_metric') THEN
    ALTER TABLE saas_feature ADD COLUMN usage_metric VARCHAR(100) NULL COMMENT '实时用量提供器编码' AFTER usage_source;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan' AND column_name='sort_order') THEN
    ALTER TABLE saas_plan ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER description;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan_version' AND column_name='monthly_price') THEN
    ALTER TABLE saas_plan_version ADD COLUMN monthly_price DECIMAL(12,2) NULL AFTER version_no;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan_version' AND column_name='yearly_price') THEN
    ALTER TABLE saas_plan_version ADD COLUMN yearly_price DECIMAL(12,2) NULL AFTER monthly_price;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan_version' AND column_name='currency') THEN
    ALTER TABLE saas_plan_version ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY' AFTER yearly_price;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan_entitlement' AND column_name='text_value') THEN
    ALTER TABLE saas_plan_entitlement ADD COLUMN text_value VARCHAR(500) NULL AFTER quota_value;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_addon' AND column_name='monthly_price') THEN
    ALTER TABLE saas_addon ADD COLUMN monthly_price DECIMAL(12,2) NULL AFTER description;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_addon' AND column_name='yearly_price') THEN
    ALTER TABLE saas_addon ADD COLUMN yearly_price DECIMAL(12,2) NULL AFTER monthly_price;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_addon' AND column_name='currency') THEN
    ALTER TABLE saas_addon ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY' AFTER yearly_price;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_addon' AND column_name='sort_order') THEN
    ALTER TABLE saas_addon ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER max_quantity;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_addon_entitlement' AND column_name='text_value') THEN
    ALTER TABLE saas_addon_entitlement ADD COLUMN text_value VARCHAR(500) NULL AFTER quota_value;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_tenant_entitlement_override' AND column_name='text_value') THEN
    ALTER TABLE saas_tenant_entitlement_override ADD COLUMN text_value VARCHAR(500) NULL AFTER quota_value;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_tenant_subscription' AND column_name='billing_cycle') THEN
    ALTER TABLE saas_tenant_subscription ADD COLUMN billing_cycle VARCHAR(16) NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY/YEARLY/CUSTOM' AFTER plan_version_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_tenant_subscription' AND column_name='auto_renew') THEN
    ALTER TABLE saas_tenant_subscription ADD COLUMN auto_renew TINYINT NOT NULL DEFAULT 0 AFTER cancel_at_period_end;
  END IF;

  UPDATE saas_feature SET usage_source='NONE' WHERE feature_type='BOOLEAN';
  UPDATE saas_feature SET feature_type='NUMBER', unit='STORE', quota_mode='RESOURCE',
      usage_source='REALTIME', usage_metric='store_count', default_value=NULL
  WHERE feature_code='store_count';
END$$
DELIMITER ;
CALL migrate_saas_commercial_20260907();
DROP PROCEDURE IF EXISTS migrate_saas_commercial_20260907;

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name LIKE 'saas_%'
  AND column_name IN ('default_text_value','allowed_values','usage_source','usage_metric','sort_order',
                      'monthly_price','yearly_price','currency','text_value','billing_cycle','auto_renew')
ORDER BY table_name, ordinal_position;
