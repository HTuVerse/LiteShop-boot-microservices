USE `liteshop_user`;

-- 平台服务器监控菜单：仅平台超级管理员可见。
SET @monitor_menu_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission);

INSERT INTO sys_permission
  (id, permission_name, permission_code, parent_id, permission_type,
   component_address, router_address, sort, status, icon, is_hide, is_iframe, keep_alive, menu_scope)
SELECT @monitor_menu_id, '服务器监控', 'platform:monitor', parent.id, 1,
       'platform/monitor/index', '/platform/monitor', 60, 1,
       'DataAnalysis', 0, 0, 1, 1
FROM sys_permission parent
WHERE parent.permission_code = 'platform:menu'
  AND NOT EXISTS (SELECT 1 FROM sys_permission existing
                  WHERE existing.permission_code = 'platform:monitor');

-- 向平台超级管理员授予菜单权限，租户角色不授予。
INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT role.tenant_id, role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.permission_code = 'platform:monitor'
WHERE role.tenant_id IS NULL AND role.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);
