-- 移除冗余的经营监控菜单、权限和持久化数据。
SET NAMES utf8mb4;
USE liteshop_user;

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE permission.permission_code = 'operations:ai:intelligence';

DELETE FROM sys_permission
WHERE permission_code = 'operations:ai:intelligence';

DROP TABLE IF EXISTS saas_ai_alert;
DROP TABLE IF EXISTS saas_ai_auto_report;
DROP TABLE IF EXISTS saas_ai_intelligence_setting;
