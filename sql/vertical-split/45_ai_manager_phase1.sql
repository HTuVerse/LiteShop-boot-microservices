USE `liteshop_user`;

-- 第一阶段沿用经营助手权限，仅升级菜单名称，避免破坏已有角色授权。
UPDATE `sys_permission`
SET `permission_name` = 'AI店长',
    `update_time` = NOW()
WHERE `permission_code` = 'operations:assistant'
  AND `permission_type` = 1;
