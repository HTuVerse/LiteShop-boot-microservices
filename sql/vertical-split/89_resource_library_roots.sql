-- 虚拟租户根目录的共享策略。根目录名称由用户服务的租户名称提供，不依赖可变路径。
USE `liteshop_file`;

CREATE TABLE IF NOT EXISTS `resource_library_root_access` (
  `tenant_id` BIGINT NOT NULL COMMENT '资源归属租户',
  `access_mode` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE / ALL / SELECTED',
  `permissions` INT NOT NULL DEFAULT 0 COMMENT 'ALL 模式位掩码：查看1 选用2 编辑4 上传8',
  PRIMARY KEY (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `resource_library_root_grant` (
  `tenant_id` BIGINT NOT NULL COMMENT '资源归属租户',
  `grantee_tenant_id` BIGINT NOT NULL COMMENT '获得只读权限的租户',
  `permissions` INT NOT NULL DEFAULT 0 COMMENT '查看1 选用2 编辑4 上传8',
  PRIMARY KEY (`tenant_id`, `grantee_tenant_id`),
  KEY `idx_resource_root_grantee` (`grantee_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 租户入口必须使用独立组件路径，避免动态菜单将 platform/ 路径认作平台专属。
USE `liteshop_user`;
UPDATE `sys_permission`
SET `component_address`='config/resources/index', `router_address`='/config/resources', `menu_scope`=2
WHERE `permission_code`='config:resources';
