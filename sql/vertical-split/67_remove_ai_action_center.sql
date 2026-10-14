-- 移除已部署环境中的行动中心菜单、权限和专属数据表。
SET NAMES utf8mb4;
USE liteshop_user;

START TRANSACTION;

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE permission.permission_code IN (
  'operations:assistant:plans',
  'operations:ai:task:create',
  'operations:ai:approve'
);

DELETE FROM sys_permission
WHERE permission_code IN (
  'operations:assistant:plans',
  'operations:ai:task:create',
  'operations:ai:approve'
);

COMMIT;

DROP TABLE IF EXISTS saas_ai_playbook;
DROP TABLE IF EXISTS saas_ai_business_task;

SET @quality_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'saas_ai_generation'
    AND index_name = 'idx_ai_generation_quality'
);
SET @drop_quality_index_sql := IF(
  @quality_index_exists > 0,
  'ALTER TABLE saas_ai_generation DROP INDEX idx_ai_generation_quality',
  'SELECT 1'
);
PREPARE drop_quality_index_statement FROM @drop_quality_index_sql;
EXECUTE drop_quality_index_statement;
DEALLOCATE PREPARE drop_quality_index_statement;
