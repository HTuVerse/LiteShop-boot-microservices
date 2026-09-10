-- LiteShop SaaS 初始套餐数据（MySQL 8，可重复执行）
-- 项目已有的点餐、会员、营销、报表等属于所有租户的基础功能，不写入 saas_feature。
-- 这里只登记已完成业务侧强制校验的增值服务。
-- 新环境先执行 22；旧表升级后依次执行 24、26_saas_plan_catalog_ui。
SET NAMES utf8mb4;
USE liteshop_user;

-- 当前唯一已落地增值服务：可管理门店数。
INSERT INTO saas_feature
  (id,feature_code,feature_name,feature_type,unit,quota_mode,usage_source,usage_metric,
   default_value,default_text_value,allowed_values,description,status,create_time,update_time)
VALUES
  (881100000000000001,'store_count','可管理门店数','NUMBER','STORE','RESOURCE','REALTIME','store_count',
   NULL,NULL,NULL,'租户可创建并保有的门店总数；新增门店时服务端强制校验',1,NOW(),NOW())
ON DUPLICATE KEY UPDATE
  feature_name=VALUES(feature_name),feature_type='NUMBER',unit='STORE',quota_mode='RESOURCE',
  usage_source='REALTIME',usage_metric='store_count',default_value=NULL,default_text_value=NULL,
  allowed_values=NULL,description=VALUES(description),status=1,update_time=NOW();

-- 所有套餐均包含系统现有基础功能，只按门店容量区分。
INSERT INTO saas_plan
  (id,plan_code,plan_name,target_customer,core_benefits,description,recommended,sort_order,status,create_time,update_time)
VALUES
  (881200000000000000,'free','免费版','适合个人试用 / 小型餐饮','全部基础经营功能\n1 家门店','轻松体验，快速上手',0,0,1,NOW(),NOW()),
  (881200000000000001,'basic','标准版','适合单店 / 小型连锁','全部基础经营功能\n3 家门店\n支持额外门店加购','提升效率，开启增长',1,10,1,NOW(),NOW()),
  (881200000000000002,'pro','专业版','适合多店连锁','全部基础经营功能\n20 家门店\n支持额外门店加购','更强的多门店经营能力',0,20,1,NOW(),NOW()),
  (881200000000000003,'chain','企业版','适合大型品牌 / 集团','全部基础经营功能\n不限门店\n支持专属服务','规模化经营解决方案',0,30,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE
  plan_name=VALUES(plan_name),target_customer=VALUES(target_customer),core_benefits=VALUES(core_benefits),
  description=VALUES(description),recommended=VALUES(recommended),sort_order=VALUES(sort_order),status=1,update_time=NOW();

SET @free_plan := (SELECT id FROM saas_plan WHERE plan_code='free' LIMIT 1);
SET @basic_plan := (SELECT id FROM saas_plan WHERE plan_code='basic' LIMIT 1);
SET @pro_plan := (SELECT id FROM saas_plan WHERE plan_code='pro' LIMIT 1);
SET @chain_plan := (SELECT id FROM saas_plan WHERE plan_code='chain' LIMIT 1);
SET @store_feature := (SELECT id FROM saas_feature WHERE feature_code='store_count' LIMIT 1);

INSERT IGNORE INTO saas_plan_version
  (id,plan_id,version_no,monthly_price,yearly_price,currency,status,effective_from,published_at,create_time,update_time)
VALUES
  (881300000000000000,@free_plan,1,0.00,0.00,'CNY','PUBLISHED',NOW(),NOW(),NOW(),NOW()),
  (881300000000000001,@basic_plan,1,99.00,999.00,'CNY','PUBLISHED',NOW(),NOW(),NOW(),NOW()),
  (881300000000000002,@pro_plan,1,299.00,2999.00,'CNY','PUBLISHED',NOW(),NOW(),NOW(),NOW()),
  (881300000000000003,@chain_plan,1,999.00,9999.00,'CNY','PUBLISHED',NOW(),NOW(),NOW(),NOW());

SET @free_v := (SELECT id FROM saas_plan_version WHERE plan_id=@free_plan AND version_no=1 LIMIT 1);
SET @basic_v := (SELECT id FROM saas_plan_version WHERE plan_id=@basic_plan AND version_no=1 LIMIT 1);
SET @pro_v := (SELECT id FROM saas_plan_version WHERE plan_id=@pro_plan AND version_no=1 LIMIT 1);
SET @chain_v := (SELECT id FROM saas_plan_version WHERE plan_id=@chain_plan AND version_no=1 LIMIT 1);

-- 每个套餐只保存真正的增值权益，不重复罗列系统菜单或基础模块。
INSERT IGNORE INTO saas_plan_entitlement
  (id,plan_version_id,feature_id,enabled,quota_value,text_value,unlimited,reset_period,enforcement,create_time,update_time)
VALUES
  (881400000000000000,@free_v,@store_feature,1,1,NULL,0,'NONE','HARD',NOW(),NOW()),
  (881400000000000001,@basic_v,@store_feature,1,3,NULL,0,'NONE','HARD',NOW(),NOW()),
  (881400000000000002,@pro_v,@store_feature,1,20,NULL,0,'NONE','HARD',NOW(),NOW()),
  (881400000000000003,@chain_v,@store_feature,1,NULL,NULL,1,'NONE','HARD',NOW(),NOW());

-- 当前唯一可售加购项：每购买一份增加 1 家门店额度。
INSERT INTO saas_addon
  (id,addon_code,addon_name,description,monthly_price,yearly_price,currency,max_quantity,sort_order,status,create_time,update_time)
VALUES
  (881500000000000001,'extra_store','额外门店','每份增加 1 家可管理门店',59,599,'CNY',100,10,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE
  addon_name=VALUES(addon_name),description=VALUES(description),monthly_price=VALUES(monthly_price),
  yearly_price=VALUES(yearly_price),currency='CNY',max_quantity=100,sort_order=10,status=1,update_time=NOW();

SET @extra_store := (SELECT id FROM saas_addon WHERE addon_code='extra_store' LIMIT 1);
INSERT IGNORE INTO saas_addon_entitlement (id,addon_id,feature_id,operation,quota_value,text_value,create_time)
VALUES
  (881600000000000001,@extra_store,@store_feature,'ADD',1,NULL,NOW());

-- 验收结果：每个套餐应恰好有一条 store_count 硬限制权益。
SELECT p.plan_code,p.plan_name,v.version_no,v.monthly_price,v.yearly_price,
       f.feature_code,e.quota_value,e.unlimited,e.enforcement
FROM saas_plan p JOIN saas_plan_version v ON v.plan_id=p.id
JOIN saas_plan_entitlement e ON e.plan_version_id=v.id
JOIN saas_feature f ON f.id=e.feature_id
WHERE p.plan_code IN ('free','basic','pro','chain')
ORDER BY p.sort_order,v.version_no;
