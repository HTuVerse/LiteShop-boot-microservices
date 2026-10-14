-- AI 店长作为独立的 SaaS 布尔权益。已发布的旧套餐版本保持不变。
-- 标准版默认关闭；商业版（原 pro/专业版）及企业版的新版本包含该权益。
SET NAMES utf8mb4;
USE liteshop_user;

INSERT INTO saas_feature
  (id, feature_code, feature_name, feature_type, usage_source, default_value,
   description, status, create_time, update_time)
VALUES
  (881100000000000002, 'ai_manager', 'AI 店长', 'BOOLEAN', 'NONE', 0,
   '租户可使用 AI 店长；模型配置和 Token 额度由平台统一管理', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  feature_name=VALUES(feature_name), feature_type='BOOLEAN', unit=NULL, quota_mode=NULL,
  usage_source='NONE', usage_metric=NULL, default_value=0, default_text_value=NULL,
  allowed_values=NULL, description=VALUES(description), status=1, update_time=NOW();

SET @ai_feature := (SELECT id FROM saas_feature WHERE feature_code='ai_manager' LIMIT 1);

-- 保留已有套餐编码和客户订阅，只调整默认 pro 档的展示名称。
UPDATE saas_plan SET plan_name='商业版', update_time=NOW()
WHERE plan_code='pro' AND plan_name='专业版';

-- 新版本复制原版本的价格及全部权益，避免直接修改已发布版本。
SET @pro_plan := (SELECT id FROM saas_plan WHERE plan_code='pro' LIMIT 1);
SET @pro_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000102 AND plan_id=@pro_plan LIMIT 1);
SET @pro_source := COALESCE(@pro_version,
  (SELECT id FROM saas_plan_version WHERE plan_id=@pro_plan AND status='PUBLISHED'
   ORDER BY version_no DESC LIMIT 1));
INSERT IGNORE INTO saas_plan_version
  (id,plan_id,version_no,monthly_price,yearly_price,currency,status,effective_from,published_at,create_time,update_time)
SELECT 881300000000000102, source.plan_id, latest.version_no+1,
       source.monthly_price,source.yearly_price,source.currency,'PUBLISHED',NOW(),NOW(),NOW(),NOW()
FROM saas_plan_version source
JOIN (SELECT COALESCE(MAX(version_no),0) AS version_no FROM saas_plan_version WHERE plan_id=@pro_plan) latest
WHERE source.id=@pro_source AND @pro_version IS NULL;
SET @pro_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000102 AND plan_id=@pro_plan LIMIT 1);
INSERT IGNORE INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT UUID_SHORT(),@pro_version,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,NOW(),NOW()
FROM saas_plan_entitlement WHERE plan_version_id=@pro_source AND @pro_source<>@pro_version;
INSERT INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT 881400000000000102,@pro_version,@ai_feature,1,NULL,NULL,0,'NONE','HARD',NOW(),NOW()
WHERE @pro_version IS NOT NULL
ON DUPLICATE KEY UPDATE enabled=1,update_time=NOW();

SET @chain_plan := (SELECT id FROM saas_plan WHERE plan_code='chain' LIMIT 1);
SET @chain_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000103 AND plan_id=@chain_plan LIMIT 1);
SET @chain_source := COALESCE(@chain_version,
  (SELECT id FROM saas_plan_version WHERE plan_id=@chain_plan AND status='PUBLISHED'
   ORDER BY version_no DESC LIMIT 1));
INSERT IGNORE INTO saas_plan_version
  (id,plan_id,version_no,monthly_price,yearly_price,currency,status,effective_from,published_at,create_time,update_time)
SELECT 881300000000000103,source.plan_id,latest.version_no+1,
       source.monthly_price,source.yearly_price,source.currency,'PUBLISHED',NOW(),NOW(),NOW(),NOW()
FROM saas_plan_version source
JOIN (SELECT COALESCE(MAX(version_no),0) AS version_no FROM saas_plan_version WHERE plan_id=@chain_plan) latest
WHERE source.id=@chain_source AND @chain_version IS NULL;
SET @chain_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000103 AND plan_id=@chain_plan LIMIT 1);
INSERT IGNORE INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT UUID_SHORT(),@chain_version,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,NOW(),NOW()
FROM saas_plan_entitlement WHERE plan_version_id=@chain_source AND @chain_source<>@chain_version;
INSERT INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT 881400000000000103,@chain_version,@ai_feature,1,NULL,NULL,0,'NONE','HARD',NOW(),NOW()
WHERE @chain_version IS NOT NULL
ON DUPLICATE KEY UPDATE enabled=1,update_time=NOW();

-- 加购价格由运营配置，当前管理后台可在完成线下计费后分配加购。
INSERT IGNORE INTO saas_addon
  (id,addon_code,addon_name,description,monthly_price,yearly_price,currency,max_quantity,sort_order,status,create_time,update_time)
VALUES (881500000000000002,'ai_manager','AI 店长加购','为当前租户单独开通 AI 店长',NULL,NULL,'CNY',1,20,1,NOW(),NOW());
SET @ai_addon := (SELECT id FROM saas_addon WHERE addon_code='ai_manager' LIMIT 1);
INSERT IGNORE INTO saas_addon_entitlement
  (id,addon_id,feature_id,operation,quota_value,text_value,create_time)
SELECT 881600000000000002,@ai_addon,@ai_feature,'ENABLE',NULL,NULL,NOW()
WHERE @ai_addon IS NOT NULL;

-- 验收：标准版关闭，商业版和企业版新版本开启；单独加购为 ENABLE。
SELECT p.plan_code,p.plan_name,v.version_no,COALESCE(e.enabled,0) AS ai_manager_enabled
FROM saas_plan p JOIN saas_plan_version v ON v.plan_id=p.id
LEFT JOIN saas_plan_entitlement e ON e.plan_version_id=v.id AND e.feature_id=@ai_feature
WHERE p.plan_code IN ('basic','pro','chain') AND v.status='PUBLISHED'
ORDER BY p.plan_code,v.version_no DESC;
SELECT a.addon_code,a.status,r.operation
FROM saas_addon a JOIN saas_addon_entitlement r ON r.addon_id=a.id
WHERE a.addon_code='ai_manager' AND r.feature_id=@ai_feature;
