-- 文件夹权限：缺少记录即继承最近的父目录/根目录策略。
USE `liteshop_file`;

CREATE TABLE IF NOT EXISTS `resource_library_folder_access` (
  `folder_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL COMMENT '资源归属租户',
  `access_mode` VARCHAR(16) NOT NULL COMMENT 'PRIVATE / ALL / SELECTED',
  `permissions` INT NOT NULL DEFAULT 0 COMMENT 'ALL 模式位掩码：查看1 选用2 编辑4 上传8',
  PRIMARY KEY (`folder_id`),
  KEY `idx_folder_access_tenant` (`tenant_id`),
  CONSTRAINT `fk_folder_access_folder` FOREIGN KEY (`folder_id`) REFERENCES `resource_folder` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `resource_library_folder_grant` (
  `folder_id` BIGINT NOT NULL,
  `grantee_tenant_id` BIGINT NOT NULL,
  `permissions` INT NOT NULL DEFAULT 0 COMMENT '查看1 选用2 编辑4 上传8',
  PRIMARY KEY (`folder_id`, `grantee_tenant_id`),
  KEY `idx_folder_grant_tenant` (`grantee_tenant_id`),
  CONSTRAINT `fk_folder_grant_folder` FOREIGN KEY (`folder_id`) REFERENCES `resource_folder` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
