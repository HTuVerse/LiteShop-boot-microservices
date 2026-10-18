-- 依次执行 75_resource_library.sql 与本迁移，再部署新文件服务。
USE `liteshop_config`;

CREATE TABLE IF NOT EXISTS `file_storage_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '不可变配置快照ID',
  `config_json` json NOT NULL COMMENT '含加密凭据的存储配置快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史文件存储配置快照';

-- 先固定升级前的 Local/七牛配置，历史文件全部指向快照 1。
INSERT INTO `file_storage_profile` (`id`, `config_json`)
SELECT 1, JSON_SET(`config`.`config_value`, '$.profileId', 1,
                   '$.storageMode', IF(JSON_UNQUOTE(JSON_EXTRACT(`config`.`config_value`, '$.ossEnabled')) = 'true', 'QINIU', 'LOCAL'))
FROM `sys_website_config` AS `config`
WHERE `config`.`id` = 3
  AND NOT EXISTS (SELECT 1 FROM `file_storage_profile` AS `existing` WHERE `existing`.`id` = 1);

UPDATE `sys_website_config`
SET `config_value` = JSON_SET(`config_value`, '$.profileId', 1,
                           '$.storageMode', IF(JSON_UNQUOTE(JSON_EXTRACT(`config_value`, '$.ossEnabled')) = 'true', 'QINIU', 'LOCAL'))
WHERE `id` = 3 AND JSON_EXTRACT(`config_value`, '$.profileId') IS NULL;

USE `liteshop_file`;
ALTER TABLE `file_upload`
  MODIFY COLUMN `storage_type` tinyint NULL COMMENT '存储驱动：0 Local，1 七牛历史，2 MinIO，3 阿里云 OSS',
  ADD COLUMN `storage_key` varchar(255) NULL COMMENT '对象 Key 或本地相对路径',
  ADD COLUMN `storage_profile_id` bigint NULL COMMENT '上传时绑定的不可变存储配置快照ID',
  ADD COLUMN `bucket` varchar(128) NULL COMMENT '实际存储桶；本地为public或private目录标识',
  ADD COLUMN `visibility` varchar(10) NOT NULL DEFAULT 'PUBLIC' COMMENT '访问权限：PUBLIC 公开，PRIVATE 私有',
  ADD COLUMN `file_extension` varchar(12) NULL COMMENT '文件扩展名',
  ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  ADD INDEX `idx_file_profile` (`storage_profile_id`),
  ADD INDEX `idx_file_visibility` (`visibility`);

UPDATE `file_upload` SET `storage_profile_id` = 1 WHERE `storage_profile_id` IS NULL;
