USE liteshop_user;

SET @payment_account_menu_id := IF(NOT EXISTS(SELECT 1 FROM sys_permission WHERE id = 52), 52,
  (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission));

INSERT INTO sys_permission
  (id, permission_name, permission_code, parent_id, permission_type,
   component_address, router_address, sort, status, icon, is_hide, is_iframe, keep_alive, menu_scope)
SELECT @payment_account_menu_id, '支付账户', 'config:payment-account', parent_permission.id, 1,
       'config/payment-account/index', '/config/payment-account', 52, 1,
       'Wallet', 0, 0, 1, 2
FROM sys_permission parent_permission
WHERE parent_permission.permission_code = 'config:menu'
  AND NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.permission_code = 'config:payment-account'
       OR existing.router_address = '/config/payment-account'
  );

UPDATE sys_permission
SET status = 1, menu_scope = 2
WHERE permission_code = 'config:payment-account';

-- 只授予平台超级管理员和租户总部管理员。门店角色不能查看或修改支付账户。
INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT role.tenant_id, role.id, permission.id
FROM sys_role role
INNER JOIN sys_permission permission
  ON permission.permission_code = 'config:payment-account'
WHERE (role.tenant_id IS NULL AND role.role_code = 'SUPER_ADMIN')
   OR (role.tenant_id IS NOT NULL AND role.role_code = 'ADMIN')
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);
