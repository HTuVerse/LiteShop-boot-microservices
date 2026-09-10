-- 已停用：旧演示脚本把基础功能和未接入业务限制的能力写成了 SaaS feature。
-- 请执行 25_saas_restaurant_plans.sql 初始化当前有效的套餐数据。
/*
-- 以下旧数据仅保留作为历史参考，不再执行。
-- SaaS 套餐演示数据（MySQL 8，可重复执行）
-- 前置条件：已执行 22_saas_entitlements.sql。
-- 默认只创建功能、套餐版本及加购项，不绑定任何租户。
-- 如需给测试租户绑定“专业版”，请将下面的 NULL 改为真实 tenant_id 后再执行。
SET @demo_tenant_id := NULL;

SET NAMES utf8mb4;
USE liteshop_user;

-- 如果执行过旧版脚本并出现 1205，请先在原 SQL 会话执行 ROLLBACK，
-- 或关闭原会话后使用新连接执行本脚本。
-- 演示数据使用独立的 880x 固定 ID 段，不开启长事务，也不再通过 MAX(id) 分配 ID。

-- 1. 功能目录
INSERT IGNORE INTO saas_feature
  (id, feature_code, feature_name, feature_type, unit, quota_mode, default_value, description, status, create_time, update_time)
VALUES
  (880100000000000001, 'demo.store.count', '门店数量', 'QUOTA', 'COUNT', 'RESOURCE', 0,
   '演示：租户最多可创建的门店数', 1, NOW(), NOW()),
  (880100000000000002, 'demo.member.count', '成员席位', 'QUOTA', 'SEAT', 'RESOURCE', 0,
   '演示：租户最多可启用的成员数', 1, NOW(), NOW()),
  (880100000000000003, 'demo.api.requests.monthly', '每月 API 请求', 'QUOTA', 'REQUEST', 'PERIODIC', 0,
   '演示：每个自然月允许消费的 API 请求数', 1, NOW(), NOW()),
  (880100000000000004, 'demo.storage.bytes', '文件存储空间', 'QUOTA', 'BYTE', 'RESOURCE', 0,
   '演示：租户可占用的文件存储字节数', 1, NOW(), NOW()),
  (880100000000000005, 'demo.data.export', '数据导出', 'BOOLEAN', NULL, NULL, NULL,
   '演示：是否允许导出业务数据', 1, NOW(), NOW()),
  (880100000000000006, 'demo.audit.log', '审计日志', 'BOOLEAN', NULL, NULL, NULL,
   '演示：是否启用审计日志能力', 1, NOW(), NOW());

-- 重复执行时仅刷新演示记录的展示信息，不影响非 demo 数据。
UPDATE saas_feature SET feature_name='门店数量', feature_type='QUOTA', unit='COUNT', quota_mode='RESOURCE', usage_source='REALTIME', usage_metric='store_count', status=1, update_time=NOW()
WHERE feature_code='demo.store.count';
UPDATE saas_feature SET feature_name='成员席位', feature_type='QUOTA', unit='SEAT', quota_mode='RESOURCE', usage_source='COUNTER', usage_metric=NULL, status=1, update_time=NOW()
WHERE feature_code='demo.member.count';
UPDATE saas_feature SET feature_name='每月 API 请求', feature_type='QUOTA', unit='REQUEST', quota_mode='PERIODIC', usage_source='COUNTER', usage_metric=NULL, status=1, update_time=NOW()
WHERE feature_code='demo.api.requests.monthly';
UPDATE saas_feature SET feature_name='文件存储空间', feature_type='QUOTA', unit='BYTE', quota_mode='RESOURCE', usage_source='COUNTER', usage_metric=NULL, status=1, update_time=NOW()
WHERE feature_code='demo.storage.bytes';
UPDATE saas_feature SET feature_name='数据导出', feature_type='BOOLEAN', unit=NULL, quota_mode=NULL, usage_source='NONE', usage_metric=NULL, status=1, update_time=NOW()
WHERE feature_code='demo.data.export';
UPDATE saas_feature SET feature_name='审计日志', feature_type='BOOLEAN', unit=NULL, quota_mode=NULL, usage_source='NONE', usage_metric=NULL, status=1, update_time=NOW()
WHERE feature_code='demo.audit.log';

-- 2. 套餐主档
INSERT IGNORE INTO saas_plan (id, plan_code, plan_name, description, status, create_time, update_time)
VALUES
  (880200000000000001, 'demo.starter', '入门版（演示）', '适合单店和小团队', 1, NOW(), NOW()),
  (880200000000000002, 'demo.pro', '专业版（演示）', '适合成长型连锁商家', 1, NOW(), NOW()),
  (880200000000000003, 'demo.enterprise', '企业版（演示）', '不限核心资源并开放全部能力', 1, NOW(), NOW());

UPDATE saas_plan SET plan_name='入门版（演示）', description='适合单店和小团队', status=1, update_time=NOW() WHERE plan_code='demo.starter';
UPDATE saas_plan SET plan_name='专业版（演示）', description='适合成长型连锁商家', status=1, update_time=NOW() WHERE plan_code='demo.pro';
UPDATE saas_plan SET plan_name='企业版（演示）', description='不限核心资源并开放全部能力', status=1, update_time=NOW() WHERE plan_code='demo.enterprise';

SET @starter_plan_id := (SELECT id FROM saas_plan WHERE plan_code='demo.starter');
SET @pro_plan_id := (SELECT id FROM saas_plan WHERE plan_code='demo.pro');
SET @enterprise_plan_id := (SELECT id FROM saas_plan WHERE plan_code='demo.enterprise');

-- 每个演示套餐固定使用 v1。已发布版本视为不可变；本脚本只在不存在时创建。
INSERT IGNORE INTO saas_plan_version
  (id, plan_id, version_no, status, effective_from, effective_to, published_at, create_time, update_time)
VALUES
  (880300000000000001, @starter_plan_id, 1, 'PUBLISHED', NOW(), NULL, NOW(), NOW(), NOW()),
  (880300000000000002, @pro_plan_id, 1, 'PUBLISHED', NOW(), NULL, NOW(), NOW(), NOW()),
  (880300000000000003, @enterprise_plan_id, 1, 'PUBLISHED', NOW(), NULL, NOW(), NOW(), NOW());

SET @starter_version_id := (SELECT id FROM saas_plan_version WHERE plan_id=@starter_plan_id AND version_no=1);
SET @pro_version_id := (SELECT id FROM saas_plan_version WHERE plan_id=@pro_plan_id AND version_no=1);
SET @enterprise_version_id := (SELECT id FROM saas_plan_version WHERE plan_id=@enterprise_plan_id AND version_no=1);

SET @store_feature_id := (SELECT id FROM saas_feature WHERE feature_code='demo.store.count');
SET @member_feature_id := (SELECT id FROM saas_feature WHERE feature_code='demo.member.count');
SET @api_feature_id := (SELECT id FROM saas_feature WHERE feature_code='demo.api.requests.monthly');
SET @storage_feature_id := (SELECT id FROM saas_feature WHERE feature_code='demo.storage.bytes');
SET @export_feature_id := (SELECT id FROM saas_feature WHERE feature_code='demo.data.export');
SET @audit_feature_id := (SELECT id FROM saas_feature WHERE feature_code='demo.audit.log');

-- 3. 套餐权益矩阵。只为缺失规则补数据，不修改已存在的已发布版本。
-- 入门版：1 门店、5 席位、1000 API/月、1 GiB，无导出、无审计。
INSERT IGNORE INTO saas_plan_entitlement
  (id, plan_version_id, feature_id, enabled, quota_value, unlimited, reset_period, enforcement, create_time, update_time)
VALUES
  (880400000000000001, @starter_version_id, @store_feature_id,   1, 1,          0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000002, @starter_version_id, @member_feature_id,  1, 5,          0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000003, @starter_version_id, @api_feature_id,     1, 1000,       0, 'MONTH', 'HARD', NOW(), NOW()),
  (880400000000000004, @starter_version_id, @storage_feature_id, 1, 1073741824, 0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000005, @starter_version_id, @export_feature_id,  0, NULL,       0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000006, @starter_version_id, @audit_feature_id,   0, NULL,       0, 'NONE',  'HARD', NOW(), NOW());

-- 专业版：5 门店、50 席位、100000 API/月、100 GiB，开放导出和审计。
INSERT IGNORE INTO saas_plan_entitlement
  (id, plan_version_id, feature_id, enabled, quota_value, unlimited, reset_period, enforcement, create_time, update_time)
VALUES
  (880400000000000007, @pro_version_id, @store_feature_id,   1, 5,            0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000008, @pro_version_id, @member_feature_id,  1, 50,           0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000009, @pro_version_id, @api_feature_id,     1, 100000,       0, 'MONTH', 'HARD', NOW(), NOW()),
  (880400000000000010, @pro_version_id, @storage_feature_id, 1, 107374182400, 0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000011, @pro_version_id, @export_feature_id,  1, NULL,         0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000012, @pro_version_id, @audit_feature_id,   1, NULL,         0, 'NONE',  'HARD', NOW(), NOW());

-- 企业版：全部功能启用，四项配额均不限量。
INSERT IGNORE INTO saas_plan_entitlement
  (id, plan_version_id, feature_id, enabled, quota_value, unlimited, reset_period, enforcement, create_time, update_time)
VALUES
  (880400000000000013, @enterprise_version_id, @store_feature_id,   1, NULL, 1, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000014, @enterprise_version_id, @member_feature_id,  1, NULL, 1, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000015, @enterprise_version_id, @api_feature_id,     1, NULL, 1, 'MONTH', 'HARD', NOW(), NOW()),
  (880400000000000016, @enterprise_version_id, @storage_feature_id, 1, NULL, 1, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000017, @enterprise_version_id, @export_feature_id,  1, NULL, 0, 'NONE',  'HARD', NOW(), NOW()),
  (880400000000000018, @enterprise_version_id, @audit_feature_id,   1, NULL, 0, 'NONE',  'HARD', NOW(), NOW());

-- 4. 加购项
INSERT IGNORE INTO saas_addon
  (id, addon_code, addon_name, description, max_quantity, status, create_time, update_time)
VALUES
  (880500000000000001, 'demo.extra.store', '增加 1 个门店', '每份增加 1 个门店额度', 100, 1, NOW(), NOW()),
  (880500000000000002, 'demo.extra.members.10', '增加 10 个成员', '每份增加 10 个成员席位', 100, 1, NOW(), NOW()),
  (880500000000000003, 'demo.extra.storage.100g', '增加 100 GiB 存储', '每份增加 100 GiB 文件存储', 100, 1, NOW(), NOW()),
  (880500000000000004, 'demo.unlock.audit', '开通审计日志', '为未包含审计能力的套餐开通审计日志', 1, 1, NOW(), NOW());

SET @store_addon_id := (SELECT id FROM saas_addon WHERE addon_code='demo.extra.store');
SET @member_addon_id := (SELECT id FROM saas_addon WHERE addon_code='demo.extra.members.10');
SET @storage_addon_id := (SELECT id FROM saas_addon WHERE addon_code='demo.extra.storage.100g');
SET @audit_addon_id := (SELECT id FROM saas_addon WHERE addon_code='demo.unlock.audit');

INSERT IGNORE INTO saas_addon_entitlement
  (id, addon_id, feature_id, operation, quota_value, create_time)
VALUES
  (880600000000000001, @store_addon_id, @store_feature_id, 'ADD', 1, NOW()),
  (880600000000000002, @member_addon_id, @member_feature_id, 'ADD', 10, NOW()),
  (880600000000000003, @storage_addon_id, @storage_feature_id, 'ADD', 107374182400, NOW()),
  (880600000000000004, @audit_addon_id, @audit_feature_id, 'ENABLE', NULL, NOW());

-- 5. 可选：为指定测试租户绑定专业版 30 天。
-- 安全策略：租户已有有效订阅或已有 demo 订阅时不会覆盖、取消或重复创建。
DROP PROCEDURE IF EXISTS bind_saas_demo_tenant_20260907;
DELIMITER $$
CREATE PROCEDURE bind_saas_demo_tenant_20260907()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_subscription_id BIGINT;
  IF @demo_tenant_id IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM sys_tenant WHERE id=@demo_tenant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='@demo_tenant_id 对应的租户不存在';
    ELSEIF NOT EXISTS (
      SELECT 1 FROM saas_tenant_subscription
      WHERE tenant_id=@demo_tenant_id
        AND (
          external_subscription_id=CONCAT('demo-pro-', @demo_tenant_id)
          OR (
            status IN ('TRIALING','ACTIVE','PAST_DUE','GRACE')
            AND start_at <= NOW()
            AND (current_period_end >= NOW() OR grace_end_at >= NOW() OR trial_end_at >= NOW())
          )
        )
    ) THEN
      SET v_subscription_id = 880700000000000000 + MOD(@demo_tenant_id, 100000000000000);
      INSERT INTO saas_tenant_subscription
        (id, tenant_id, plan_version_id, status, start_at, current_period_start, current_period_end,
         cancel_at_period_end, external_subscription_id, create_time, update_time)
      VALUES
        (v_subscription_id, @demo_tenant_id, @pro_version_id, 'ACTIVE', NOW(), NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY),
         0, CONCAT('demo-pro-', @demo_tenant_id), NOW(), NOW());
      UPDATE sys_tenant SET expire_time=DATE_ADD(NOW(), INTERVAL 30 DAY), update_time=NOW()
      WHERE id=@demo_tenant_id;
    END IF;
  END IF;
END$$
DELIMITER ;
CALL bind_saas_demo_tenant_20260907();
DROP PROCEDURE IF EXISTS bind_saas_demo_tenant_20260907;

-- 执行结果概览
SELECT p.plan_code, p.plan_name, v.version_no, v.status,
       COUNT(e.id) AS entitlement_count
FROM saas_plan p
JOIN saas_plan_version v ON v.plan_id=p.id
LEFT JOIN saas_plan_entitlement e ON e.plan_version_id=v.id
WHERE p.plan_code LIKE 'demo.%'
GROUP BY p.id, p.plan_code, p.plan_name, v.id, v.version_no, v.status
ORDER BY p.plan_code, v.version_no;

SELECT addon_code, addon_name, max_quantity, status
FROM saas_addon WHERE addon_code LIKE 'demo.%' ORDER BY addon_code;
*/
SELECT 'SKIPPED: use 25_saas_restaurant_plans.sql' AS migration_notice;
