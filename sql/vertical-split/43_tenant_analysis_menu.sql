USE `liteshop_user`;
-- 定价中心使用38，总部分析优先使用39；按编码保留历史菜单ID。
SET @tenant_analysis_menu_id := IF(NOT EXISTS(SELECT 1 FROM sys_permission WHERE id=39),39,
  (SELECT COALESCE(MAX(id),0)+1 FROM sys_permission));

-- 为现有数据库补充租户经营分析菜单；新建数据库已由 08_seed_development.sql 提供。
INSERT INTO `sys_permission`
  (`id`, `permission_name`, `permission_code`, `parent_id`, `permission_type`,
   `component_address`, `router_address`, `sort`, `status`, `icon`, `is_hide`, `is_iframe`, `keep_alive`, `menu_scope`)
SELECT @tenant_analysis_menu_id, '租户经营分析', 'restaurant:tenant-analysis', parent_permission.id, 1,
       'restaurant/tenant-analysis/index', '/restaurant/tenant-analysis', 39, 1,
       'DataAnalysis', 0, 0, 1, 2
FROM `sys_permission` parent_permission
WHERE parent_permission.permission_code = 'restaurant:menu'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_permission` existing
    WHERE existing.permission_code = 'restaurant:tenant-analysis'
       OR existing.router_address = '/restaurant/tenant-analysis'
  );

-- 显式执行此初始化脚本时启用总部分析，兼容已有菜单被停用的测试环境。
UPDATE `sys_permission`
SET `status` = 1, `menu_scope` = 2
WHERE `permission_code` = 'restaurant:tenant-analysis';

-- 仅默认授予平台超级管理员和租户总部管理员，门店角色不应看到跨门店数据。
INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT role.tenant_id, role.id, permission.id
FROM `sys_role` role
INNER JOIN `sys_permission` permission
  ON permission.permission_code = 'restaurant:tenant-analysis'
WHERE (role.tenant_id IS NULL AND role.role_code = 'SUPER_ADMIN')
   OR (role.tenant_id IS NOT NULL AND role.role_code = 'ADMIN')
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);
