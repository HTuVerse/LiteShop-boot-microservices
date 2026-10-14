USE liteshop_user;

-- 将原“平台管理”下的套餐入口提升为顶级目录，并保留原权限ID与角色授权。
SET @saas_root_id := (
  SELECT id FROM sys_permission WHERE permission_code = 'platform:saas' LIMIT 1
);
SET @saas_root_id := COALESCE(
  @saas_root_id,
  IF(NOT EXISTS(SELECT 1 FROM sys_permission WHERE id = 57), 57,
    (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission permission_snapshot))
);

INSERT INTO sys_permission
  (id, permission_name, permission_code, parent_id, permission_type,
   component_address, router_address, sort, status, icon, is_hide, is_iframe, keep_alive, menu_scope)
SELECT @saas_root_id, '套餐与权益管理', 'platform:saas', 0, 1,
       'Layout', '/saas', 15, 1, 'ri:vip-diamond-line', 0, 0, 0, 1
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission WHERE permission_code = 'platform:saas'
);

UPDATE sys_permission
SET permission_name = '套餐与权益管理',
    parent_id = 0,
    permission_type = 1,
    component_address = 'Layout',
    router_address = '/saas',
    sort = 15,
    status = 1,
    icon = 'ri:vip-diamond-line',
    is_hide = 0,
    is_iframe = 0,
    keep_alive = 0,
    menu_scope = 1
WHERE id = @saas_root_id;

SET @saas_plans_id := IFNULL(
  (SELECT id FROM sys_permission WHERE permission_code = 'platform:saas:plans' LIMIT 1),
  (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission permission_snapshot)
);
INSERT INTO sys_permission
  (id, permission_name, permission_code, parent_id, permission_type,
   component_address, router_address, sort, status, icon, is_hide, is_iframe, keep_alive, menu_scope)
VALUES
  (@saas_plans_id, '套餐权益', 'platform:saas:plans', @saas_root_id, 1,
   'platform/saas/plans/index', '/saas/plans', 1, 1, 'ri:box-3-line', 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name), parent_id = VALUES(parent_id),
  component_address = VALUES(component_address), router_address = VALUES(router_address),
  sort = VALUES(sort), status = VALUES(status), icon = VALUES(icon), menu_scope = VALUES(menu_scope);

SET @saas_features_id := IFNULL(
  (SELECT id FROM sys_permission WHERE permission_code = 'platform:saas:features' LIMIT 1),
  (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission permission_snapshot)
);
INSERT INTO sys_permission
  (id, permission_name, permission_code, parent_id, permission_type,
   component_address, router_address, sort, status, icon, is_hide, is_iframe, keep_alive, menu_scope)
VALUES
  (@saas_features_id, '功能权益', 'platform:saas:features', @saas_root_id, 1,
   'platform/saas/features/index', '/saas/features', 2, 1, 'ri:apps-2-line', 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name), parent_id = VALUES(parent_id),
  component_address = VALUES(component_address), router_address = VALUES(router_address),
  sort = VALUES(sort), status = VALUES(status), icon = VALUES(icon), menu_scope = VALUES(menu_scope);

SET @saas_tenants_id := IFNULL(
  (SELECT id FROM sys_permission WHERE permission_code = 'platform:saas:tenants' LIMIT 1),
  (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission permission_snapshot)
);
INSERT INTO sys_permission
  (id, permission_name, permission_code, parent_id, permission_type,
   component_address, router_address, sort, status, icon, is_hide, is_iframe, keep_alive, menu_scope)
VALUES
  (@saas_tenants_id, '租户权益', 'platform:saas:tenants', @saas_root_id, 1,
   'platform/saas/tenants/index', '/saas/tenants', 3, 1, 'ri:building-2-line', 0, 0, 1, 1)
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name), parent_id = VALUES(parent_id),
  component_address = VALUES(component_address), router_address = VALUES(router_address),
  sort = VALUES(sort), status = VALUES(status), icon = VALUES(icon), menu_scope = VALUES(menu_scope);

-- 原来拥有套餐管理入口的角色自动继承三个子菜单，避免升级后出现空目录。
INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT root_grant.tenant_id, root_grant.role_id, child.id
FROM sys_role_permission root_grant
INNER JOIN sys_permission child
  ON child.permission_code IN (
    'platform:saas:plans',
    'platform:saas:features',
    'platform:saas:tenants'
  )
WHERE root_grant.permission_id = @saas_root_id
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);

-- 新建环境没有旧授权时，确保平台超级管理员能够进入该目录。
INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT NULL, role.id, permission.id
FROM sys_role role
INNER JOIN sys_permission permission
  ON permission.permission_code IN (
    'platform:saas',
    'platform:saas:plans',
    'platform:saas:features',
    'platform:saas:tenants'
  )
WHERE role.tenant_id IS NULL AND role.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);
