-- 租户资源库、子文件夹和套餐存储权益；先执行文件库变更，再执行用户库种子。
USE `liteshop_file`;

CREATE TABLE IF NOT EXISTS `resource_quota_lock` (
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID，也是容量校验的串行锁键',
  PRIMARY KEY (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户上传容量校验锁';

ALTER TABLE `resource_folder`
  ADD COLUMN `parent_id` BIGINT NULL COMMENT '父文件夹ID，空表示根目录' AFTER `tenant_id`,
  ADD COLUMN `parent_scope` BIGINT GENERATED ALWAYS AS (COALESCE(`parent_id`,0)) STORED COMMENT '根目录统一去重键',
  DROP INDEX `uk_resource_folder_name`,
  ADD UNIQUE KEY `uk_resource_folder_scope` (`tenant_id`, `parent_scope`, `name`),
  ADD KEY `idx_resource_folder_parent` (`tenant_id`, `parent_id`);

USE `liteshop_user`;

INSERT INTO `saas_feature`
  (`id`,`feature_code`,`feature_name`,`feature_type`,`unit`,`quota_mode`,`usage_source`,`usage_metric`,
   `default_value`,`description`,`status`,`create_time`,`update_time`)
VALUES
  (881100000000000087,'resource_storage_bytes','资源库总容量','NUMBER','BYTE','RESOURCE','REALTIME',
   'resource_storage_bytes',1073741824,'租户所有用户和管理端上传文件占用的字节数；默认 1 GB',1,NOW(),NOW())
ON DUPLICATE KEY UPDATE `feature_name`=VALUES(`feature_name`),`status`=1,`update_time`=NOW();

SET @resource_feature := (SELECT `id` FROM `saas_feature` WHERE `feature_code`='resource_storage_bytes' LIMIT 1);

-- 已发布版本保持不可变；新租户和老租户的容量由平台通过权益调整分配。
INSERT INTO `saas_addon`
  (`id`,`addon_code`,`addon_name`,`description`,`monthly_price`,`yearly_price`,`currency`,
   `max_quantity`,`sort_order`,`status`,`create_time`,`update_time`)
VALUES
  (881500000000000087,'extra_resource_gb','额外资源库 1 GB','每份增加 1 GB 租户资源库容量',19,199,'CNY',100,30,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE `addon_name`=VALUES(`addon_name`),`description`=VALUES(`description`),`status`=1,`update_time`=NOW();

SET @resource_addon := (SELECT `id` FROM `saas_addon` WHERE `addon_code`='extra_resource_gb' LIMIT 1);
INSERT IGNORE INTO `saas_addon_entitlement`
  (`id`,`addon_id`,`feature_id`,`operation`,`quota_value`,`text_value`,`create_time`)
VALUES (881600000000000087,@resource_addon,@resource_feature,'ADD',1073741824,NULL,NOW());

-- 租户管理员看到自己的资源库；平台入口继续保留跨租户审计能力。
SET @resource_parent := (SELECT `id` FROM `sys_permission` WHERE `permission_code`='config:menu' LIMIT 1);
SET @resource_menu := (SELECT COALESCE(MAX(`id`),0)+1 FROM `sys_permission`);
INSERT INTO `sys_permission`
  (`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,
   `component_address`,`router_address`,`sort`,`status`,`icon`,`is_hide`,`is_iframe`,`keep_alive`,`menu_scope`)
SELECT @resource_menu,'我的资源库','config:resources',COALESCE(@resource_parent,0),1,
  'config/resources/index','/config/resources',25,1,'ri:folder-image-line',0,0,1,2
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_code`='config:resources');

INSERT INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT r.`tenant_id`,r.`id`,p.`id` FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code`='config:resources'
WHERE r.`tenant_id` IS NOT NULL AND r.`role_code`='ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp
                  WHERE rp.`role_id`=r.`id` AND rp.`permission_id`=p.`id`);
