-- 资源库升级：先执行文件库字段，再登记平台菜单。
USE `liteshop_file`;

ALTER TABLE `file_upload`
  ADD COLUMN `uploader_id` bigint NULL COMMENT '上传用户ID，历史记录无法追溯时为空',
  ADD COLUMN `folder_id` bigint NULL COMMENT '资源库分类文件夹ID，空表示未分类',
  ADD INDEX `idx_file_folder` (`folder_id`),
  ADD INDEX `idx_file_uploader` (`uploader_id`),
  ADD INDEX `idx_file_tenant_created` (`tenant_id`, `created_at`);

CREATE TABLE IF NOT EXISTS `resource_folder` (
  `id` bigint NOT NULL COMMENT '文件夹ID',
  `name` varchar(255) NOT NULL COMMENT '分类名称',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '归属平台ID，固定为0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_folder_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台资源库分类文件夹';

USE `liteshop_user`;
SET @resource_parent := (SELECT id FROM sys_permission WHERE permission_code = 'platform:menu' LIMIT 1);
SET @resource_id := COALESCE(
  (SELECT id FROM sys_permission WHERE permission_code = 'platform:resources' LIMIT 1),
  (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_permission permission_snapshot)
);
INSERT INTO sys_permission
  (id, permission_name, permission_code, parent_id, permission_type,
   component_address, router_address, sort, status, icon, is_hide, is_iframe, keep_alive, menu_scope)
SELECT @resource_id, '资源库', 'platform:resources', @resource_parent, 1,
       'platform/resources/index', '/platform/resources', 13, 1, 'ri:folder-image-line', 0, 0, 1, 1
WHERE @resource_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'platform:resources');

INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT NULL, r.id, @resource_id FROM sys_role r
WHERE r.role_code = 'SUPER_ADMIN' AND r.tenant_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission p WHERE p.role_id = r.id AND p.permission_id = @resource_id);
