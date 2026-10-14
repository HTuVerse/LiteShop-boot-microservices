-- AI 店长按订阅周期计次；先执行 48_ai_manager_saas_entitlement.sql。
SET NAMES utf8mb4;
USE liteshop_user;

INSERT INTO saas_feature
  (id,feature_code,feature_name,feature_type,unit,quota_mode,usage_source,default_value,
   description,status,create_time,update_time)
VALUES (881100000000000003,'ai_manager_calls','AI 店长生成次数','NUMBER','REQUEST','PERIODIC','COUNTER',NULL,
        '每个订阅周期可成功生成的 AI 店长分析次数',1,NOW(),NOW())
ON DUPLICATE KEY UPDATE feature_name=VALUES(feature_name),feature_type='NUMBER',unit='REQUEST',
  quota_mode='PERIODIC',usage_source='COUNTER',usage_metric=NULL,default_value=NULL,
  default_text_value=NULL,allowed_values=NULL,description=VALUES(description),status=1,update_time=NOW();

SET @ai_feature := (SELECT id FROM saas_feature WHERE feature_code='ai_manager' LIMIT 1);
SET @calls_feature := (SELECT id FROM saas_feature WHERE feature_code='ai_manager_calls' LIMIT 1);

-- 已发布版本不可编辑：商业版和企业版复制最新版本并分别增加 100/500 次。
SET @pro_plan := (SELECT id FROM saas_plan WHERE plan_code='pro' LIMIT 1);
SET @pro_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000202 AND plan_id=@pro_plan LIMIT 1);
SET @pro_source := COALESCE(@pro_version,
  (SELECT id FROM saas_plan_version WHERE plan_id=@pro_plan AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1));
INSERT IGNORE INTO saas_plan_version
  (id,plan_id,version_no,monthly_price,yearly_price,currency,status,effective_from,published_at,create_time,update_time)
SELECT 881300000000000202,source.plan_id,latest.version_no+1,
       source.monthly_price,source.yearly_price,source.currency,'PUBLISHED',NOW(),NOW(),NOW(),NOW()
FROM saas_plan_version source
JOIN (SELECT COALESCE(MAX(version_no),0) AS version_no FROM saas_plan_version WHERE plan_id=@pro_plan) latest
WHERE source.id=@pro_source AND @pro_version IS NULL;
SET @pro_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000202 AND plan_id=@pro_plan LIMIT 1);
INSERT IGNORE INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT UUID_SHORT(),@pro_version,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,NOW(),NOW()
FROM saas_plan_entitlement WHERE plan_version_id=@pro_source AND @pro_source<>@pro_version;
INSERT INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT 881400000000000302,@pro_version,@ai_feature,1,NULL,NULL,0,'NONE','HARD',NOW(),NOW()
WHERE @pro_version IS NOT NULL
ON DUPLICATE KEY UPDATE enabled=1,unlimited=0,reset_period='NONE',enforcement='HARD',update_time=NOW();
INSERT INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT 881400000000000303,@pro_version,@calls_feature,1,100,NULL,0,'BILLING_CYCLE','HARD',NOW(),NOW()
WHERE @pro_version IS NOT NULL
ON DUPLICATE KEY UPDATE enabled=1,quota_value=100,unlimited=0,
  reset_period='BILLING_CYCLE',enforcement='HARD',update_time=NOW();

SET @chain_plan := (SELECT id FROM saas_plan WHERE plan_code='chain' LIMIT 1);
SET @chain_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000203 AND plan_id=@chain_plan LIMIT 1);
SET @chain_source := COALESCE(@chain_version,
  (SELECT id FROM saas_plan_version WHERE plan_id=@chain_plan AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1));
INSERT IGNORE INTO saas_plan_version
  (id,plan_id,version_no,monthly_price,yearly_price,currency,status,effective_from,published_at,create_time,update_time)
SELECT 881300000000000203,source.plan_id,latest.version_no+1,
       source.monthly_price,source.yearly_price,source.currency,'PUBLISHED',NOW(),NOW(),NOW(),NOW()
FROM saas_plan_version source
JOIN (SELECT COALESCE(MAX(version_no),0) AS version_no FROM saas_plan_version WHERE plan_id=@chain_plan) latest
WHERE source.id=@chain_source AND @chain_version IS NULL;
SET @chain_version := (SELECT id FROM saas_plan_version WHERE id=881300000000000203 AND plan_id=@chain_plan LIMIT 1);
INSERT IGNORE INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT UUID_SHORT(),@chain_version,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,NOW(),NOW()
FROM saas_plan_entitlement WHERE plan_version_id=@chain_source AND @chain_source<>@chain_version;
INSERT INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT 881400000000000304,@chain_version,@ai_feature,1,NULL,NULL,0,'NONE','HARD',NOW(),NOW()
WHERE @chain_version IS NOT NULL
ON DUPLICATE KEY UPDATE enabled=1,unlimited=0,reset_period='NONE',enforcement='HARD',update_time=NOW();
INSERT INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
SELECT 881400000000000305,@chain_version,@calls_feature,1,500,NULL,0,'BILLING_CYCLE','HARD',NOW(),NOW()
WHERE @chain_version IS NOT NULL
ON DUPLICATE KEY UPDATE enabled=1,quota_value=500,unlimited=0,
  reset_period='BILLING_CYCLE',enforcement='HARD',update_time=NOW();

-- 原 AI 店长加购升级为周期 100 次包；单份同时打开功能开关。
UPDATE saas_addon SET addon_name='AI 店长 100 次包',
  description='每个订阅周期增加 100 次 AI 店长生成次数',max_quantity=10,update_time=NOW()
WHERE addon_code='ai_manager';
SET @ai_addon := (SELECT id FROM saas_addon WHERE addon_code='ai_manager' LIMIT 1);
INSERT IGNORE INTO saas_addon_entitlement
  (id,addon_id,feature_id,operation,quota_value,text_value,create_time)
SELECT 881600000000000003,@ai_addon,@calls_feature,'ADD',100,NULL,NOW()
WHERE @ai_addon IS NOT NULL;

-- 老加购记录保存了购买时快照，补齐其周期次数；重复执行不会再追加。
UPDATE saas_subscription_addon
SET entitlement_snapshot=JSON_ARRAY_APPEND(entitlement_snapshot,'$',
      JSON_OBJECT('featureId',@calls_feature,'operation','ADD','quotaValue',100,'textValue',NULL)),
    update_time=NOW()
WHERE addon_id=@ai_addon AND status='ACTIVE'
  AND NOT JSON_CONTAINS(entitlement_snapshot,JSON_OBJECT('featureId',@calls_feature));

CREATE TABLE IF NOT EXISTS saas_ai_generation (
  id BIGINT NOT NULL COMMENT '雪花ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  request_id VARCHAR(64) NOT NULL COMMENT '客户端一次生成操作的幂等ID',
  request_hash CHAR(64) NOT NULL COMMENT '请求内容摘要，防止同一ID复用不同内容',
  operation VARCHAR(32) NOT NULL COMMENT '生成类型：BRIEFING/DISH_ADVICE/MARKETING_DRAFT/CHAT',
  status VARCHAR(16) NOT NULL COMMENT '状态：PROCESSING/SUCCEEDED/FAILED',
  response_json JSON NULL COMMENT '成功结果，供同一操作重试时复用',
  prompt_tokens BIGINT NULL COMMENT '模型报告的输入Token数',
  completion_tokens BIGINT NULL COMMENT '模型报告的输出Token数',
  created_at DATETIME NOT NULL COMMENT '预占时间',
  updated_at DATETIME NOT NULL COMMENT '状态更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_saas_ai_request (tenant_id,request_id),
  KEY idx_saas_ai_processing (status,created_at)
) ENGINE=InnoDB COMMENT='AI店长生成预占、结果与Token用量';

SELECT p.plan_code,v.version_no,f.feature_code,e.enabled,e.quota_value,e.reset_period
FROM saas_plan p JOIN saas_plan_version v ON v.plan_id=p.id
JOIN saas_plan_entitlement e ON e.plan_version_id=v.id
JOIN saas_feature f ON f.id=e.feature_id
WHERE v.id IN (@pro_version,@chain_version) AND f.feature_code IN ('ai_manager','ai_manager_calls')
ORDER BY p.plan_code,f.feature_code;
