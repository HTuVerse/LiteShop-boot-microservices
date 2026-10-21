-- 补齐已存在租户的独立资源库菜单和 ADMIN 角色授权，可重复执行。
USE `liteshop_user`;

SET @resource_parent := (SELECT `id` FROM `sys_permission` WHERE `permission_code`='config:menu' LIMIT 1);
SET @resource_menu := (SELECT COALESCE(MAX(`id`),0)+1 FROM `sys_permission`);
INSERT INTO `sys_permission`
  (`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,
   `component_address`,`router_address`,`sort`,`status`,`icon`,`is_hide`,`is_iframe`,`keep_alive`,`menu_scope`)
SELECT @resource_menu,'我的资源库','config:resources',COALESCE(@resource_parent,0),1,
  'config/resources/index','/config/resources',25,1,'ri:folder-image-line',0,0,1,2
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_code`='config:resources');

UPDATE `sys_permission`
SET `permission_name`='我的资源库', `parent_id`=COALESCE(@resource_parent,0),
    `component_address`='config/resources/index', `router_address`='/config/resources',
    `permission_type`=1, `status`=1, `is_hide`=0, `menu_scope`=2
WHERE `permission_code`='config:resources';

INSERT INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT r.`tenant_id`,r.`id`,p.`id` FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code`='config:resources'
WHERE r.`tenant_id` IS NOT NULL AND r.`role_code`='ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp
                  WHERE rp.`role_id`=r.`id` AND rp.`permission_id`=p.`id`);
