-- 统一定价中心菜单与操作权限。请在 liteshop_user 数据库执行，可重复执行。
USE liteshop_user;
START TRANSACTION;
-- 优先沿用同编码菜单；旧脚本曾把38同时用于总部分析，不能按主键覆盖其他页面。
SET @pricing_menu_id := (SELECT id FROM sys_permission WHERE permission_code='restaurant:pricing' LIMIT 1);
SET @pricing_menu_id := COALESCE(@pricing_menu_id,
  IF(NOT EXISTS(SELECT 1 FROM sys_permission WHERE id=38),38,
    (SELECT GREATEST(COALESCE(MAX(id),0)+1,900117) FROM sys_permission)));

INSERT INTO `sys_permission`
(`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,`component_address`,`router_address`,
 `sort`,`status`,`icon`,`is_hide`,`is_iframe`,`keep_alive`,`menu_scope`)
VALUES
  (@pricing_menu_id,'定价中心','restaurant:pricing',30,1,'restaurant/pricing/index','/restaurant/pricing',38,1,'PriceTag',0,0,1,2),
  (900114,'新增定价','restaurant:pricing:add',@pricing_menu_id,2,NULL,NULL,1,1,NULL,1,0,0,NULL),
  (900115,'编辑定价','restaurant:pricing:edit',@pricing_menu_id,2,NULL,NULL,2,1,NULL,1,0,0,NULL),
  (900116,'删除定价','restaurant:pricing:delete',@pricing_menu_id,2,NULL,NULL,3,1,NULL,1,0,0,NULL)
ON DUPLICATE KEY UPDATE
  `permission_name`=VALUES(`permission_name`), `parent_id`=VALUES(`parent_id`),
  `permission_type`=VALUES(`permission_type`), `component_address`=VALUES(`component_address`),
  `router_address`=VALUES(`router_address`), `sort`=VALUES(`sort`), `status`=1,
  `icon`=VALUES(`icon`), `is_hide`=VALUES(`is_hide`), `keep_alive`=VALUES(`keep_alive`),
  `menu_scope`=VALUES(`menu_scope`);

-- 原本拥有菜品管理权限的角色自动获得定价中心及其操作权限。
INSERT IGNORE INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT DISTINCT role.tenant_id, rp.role_id, pricing.id
FROM `sys_role_permission` rp
JOIN `sys_role` role ON role.id=rp.role_id
JOIN `sys_permission` dish ON dish.id=rp.permission_id AND dish.permission_code='restaurant:dish'
JOIN `sys_permission` pricing ON pricing.permission_code='restaurant:pricing';

INSERT IGNORE INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT DISTINCT role.tenant_id, rp.role_id, operation.id
FROM `sys_role_permission` rp
JOIN `sys_role` role ON role.id=rp.role_id
JOIN `sys_permission` pricing ON pricing.id=rp.permission_id AND pricing.permission_code='restaurant:pricing'
JOIN `sys_permission` operation ON operation.permission_code IN
  ('restaurant:pricing:add','restaurant:pricing:edit','restaurant:pricing:delete');
COMMIT;
