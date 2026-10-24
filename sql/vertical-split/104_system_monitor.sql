-- 系统监控菜单与角色授权；B 端和 C 端数据由用户服务分别统计。
USE `liteshop_user`;
START TRANSACTION;

INSERT INTO `sys_permission`
  (`id`, `permission_name`, `permission_code`, `parent_id`, `permission_type`,
   `component_address`, `router_address`, `menu_scope`, `sort`, `status`,
   `icon`, `is_hide`, `is_iframe`, `keep_alive`, `create_time`, `update_time`)
SELECT next_id.id, '系统监控', 'system:monitor', 0, 1,
       'Layout', '/system-monitor', 0, 25, 1,
       'ri:dashboard-line', 0, 0, 0, NOW(), NOW()
FROM (SELECT COALESCE(MAX(id), 0) + 1 AS id FROM `sys_permission`) next_id
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'system:monitor');

SET @monitor_parent := (SELECT id FROM `sys_permission` WHERE `permission_code` = 'system:monitor' LIMIT 1);

INSERT INTO `sys_permission`
  (`id`, `permission_name`, `permission_code`, `parent_id`, `permission_type`,
   `component_address`, `router_address`, `menu_scope`, `sort`, `status`,
   `icon`, `is_hide`, `is_iframe`, `keep_alive`, `create_time`, `update_time`)
SELECT next_id.id, 'B端在线用户', 'system:monitor:b', @monitor_parent, 1,
       'monitor/business/index', '/system-monitor/business', 0, 26, 1,
       'ri:computer-line', 0, 0, 0, NOW(), NOW()
FROM (SELECT COALESCE(MAX(id), 0) + 1 AS id FROM `sys_permission`) next_id
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'system:monitor:b');

INSERT INTO `sys_permission`
  (`id`, `permission_name`, `permission_code`, `parent_id`, `permission_type`,
   `component_address`, `router_address`, `menu_scope`, `sort`, `status`,
   `icon`, `is_hide`, `is_iframe`, `keep_alive`, `create_time`, `update_time`)
SELECT next_id.id, 'C端在线用户', 'system:monitor:c', @monitor_parent, 1,
       'monitor/customer/index', '/system-monitor/customer', 0, 27, 1,
       'ri:smartphone-line', 0, 0, 0, NOW(), NOW()
FROM (SELECT COALESCE(MAX(id), 0) + 1 AS id FROM `sys_permission`) next_id
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'system:monitor:c');

-- 平台超管与租户管理员默认可见；门店角色与普通用户不授予。
INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT r.tenant_id, r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.permission_code IN
  ('system:monitor', 'system:monitor:b', 'system:monitor:c')
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND ((r.role_code = 'SUPER_ADMIN' AND r.tenant_id IS NULL)
       OR (r.role_code = 'ADMIN' AND r.tenant_id IS NOT NULL))
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);

COMMIT;
