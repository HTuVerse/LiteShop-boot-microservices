-- LiteShop 站内公告、阅读状态和公告管理菜单。
-- MySQL 8；可重复执行。先发布 Nacos 的 liteShop-message-service 数据源配置，再重启消息服务。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `liteshop_message`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `liteshop_message`;

CREATE TABLE IF NOT EXISTS `sys_announcement` (
  `id` BIGINT NOT NULL COMMENT '雪花ID',
  `tenant_id` BIGINT NULL COMMENT 'NULL=平台公告，否则为商户公告',
  `title` VARCHAR(200) NOT NULL,
  `content` MEDIUMTEXT NOT NULL COMMENT '富文本公告内容',
  `level` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/IMPORTANT/URGENT',
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/WITHDRAWN',
  `publisher_id` BIGINT NOT NULL,
  `publisher_name` VARCHAR(100) NOT NULL COMMENT '发布人姓名快照',
  `version` INT NOT NULL DEFAULT 1,
  `publish_time` DATETIME NULL,
  `expire_time` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_announcement_admin` (`tenant_id`,`status`,`created_at`),
  KEY `idx_announcement_visible` (`status`,`publish_time`,`expire_time`)
) ENGINE=InnoDB COMMENT='站内公告';

CREATE TABLE IF NOT EXISTS `sys_announcement_target` (
  `id` BIGINT NOT NULL COMMENT '雪花ID',
  `announcement_id` BIGINT NOT NULL,
  `target_type` VARCHAR(16) NOT NULL COMMENT '当前支持 STORE',
  `target_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_target` (`announcement_id`,`target_type`,`target_id`),
  KEY `idx_announcement_target_lookup` (`target_type`,`target_id`,`announcement_id`)
) ENGINE=InnoDB COMMENT='公告投放目标';

CREATE TABLE IF NOT EXISTS `sys_announcement_attachment` (
  `id` BIGINT NOT NULL COMMENT '雪花ID',
  `announcement_id` BIGINT NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `file_url` VARCHAR(1000) NOT NULL,
  `file_size` BIGINT NOT NULL DEFAULT 0,
  `content_type` VARCHAR(120) NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_announcement_attachment` (`announcement_id`,`sort_order`)
) ENGINE=InnoDB COMMENT='公告附件';

CREATE TABLE IF NOT EXISTS `sys_announcement_read` (
  `announcement_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NULL COMMENT '阅读时所在商户范围',
  `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`announcement_id`,`user_id`),
  KEY `idx_announcement_read_user` (`user_id`,`read_at`)
) ENGINE=InnoDB COMMENT='公告阅读状态';

-- 已执行过旧版脚本的环境也会升级为富文本容量。
ALTER TABLE `sys_announcement`
  MODIFY COLUMN `content` MEDIUMTEXT NOT NULL COMMENT '富文本公告内容';

USE `liteshop_user`;
-- 不使用 DELIMITER/存储过程，兼容只支持普通 SQL 分句执行的客户端。
START TRANSACTION;
SET @announcement_root_id := (SELECT id FROM sys_permission WHERE permission_code='message:menu' LIMIT 1);
SET @announcement_root_id := COALESCE(@announcement_root_id, (SELECT COALESCE(MAX(id),0)+1 FROM sys_permission));
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,component_address,
   router_address,sort,status,icon,is_hide,is_iframe,keep_alive,menu_scope,create_time,update_time)
SELECT @announcement_root_id,'消息中心','message:menu',0,1,'Layout','/message',55,1,
  'ri:notification-3-line',0,0,0,0,NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='message:menu');

SET @announcement_page_id := (SELECT id FROM sys_permission WHERE permission_code='message:announcement' LIMIT 1);
SET @announcement_page_id := COALESCE(@announcement_page_id, (SELECT COALESCE(MAX(id),0)+1 FROM sys_permission));
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,component_address,
   router_address,sort,status,icon,is_hide,is_iframe,keep_alive,menu_scope,create_time,update_time)
SELECT @announcement_page_id,'公告管理','message:announcement',@announcement_root_id,1,
  'system/announcement/index','/message/announcement',10,1,'ri:megaphone-line',0,0,1,0,NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='message:announcement');

SET @announcement_next_id := (SELECT COALESCE(MAX(id),0)+1 FROM sys_permission);
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,sort,status,menu_scope,create_time,update_time)
SELECT @announcement_next_id,'新增公告','message:announcement:add',@announcement_page_id,2,1,1,0,NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='message:announcement:add');
SET @announcement_next_id := @announcement_next_id+1;
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,sort,status,menu_scope,create_time,update_time)
SELECT @announcement_next_id,'编辑公告','message:announcement:edit',@announcement_page_id,2,2,1,0,NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='message:announcement:edit');
SET @announcement_next_id := @announcement_next_id+1;
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,sort,status,menu_scope,create_time,update_time)
SELECT @announcement_next_id,'发布公告','message:announcement:publish',@announcement_page_id,2,3,1,0,NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='message:announcement:publish');
SET @announcement_next_id := @announcement_next_id+1;
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,sort,status,menu_scope,create_time,update_time)
SELECT @announcement_next_id,'撤回公告','message:announcement:withdraw',@announcement_page_id,2,4,1,0,NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='message:announcement:withdraw');
SET @announcement_next_id := @announcement_next_id+1;
INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,sort,status,menu_scope,create_time,update_time)
SELECT @announcement_next_id,'删除公告','message:announcement:delete',@announcement_page_id,2,5,1,0,NOW(),NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='message:announcement:delete');

INSERT INTO sys_role_permission(tenant_id,role_id,permission_id)
SELECT r.tenant_id,r.id,p.id
FROM sys_role r JOIN sys_permission p
  ON p.permission_code IN ('message:menu','message:announcement','message:announcement:add',
    'message:announcement:edit','message:announcement:publish',
    'message:announcement:withdraw','message:announcement:delete')
WHERE ((r.role_code='SUPER_ADMIN' AND r.tenant_id IS NULL)
   OR (r.role_code='ADMIN' AND r.tenant_id IS NOT NULL))
  AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id=r.id AND rp.permission_id=p.id);
COMMIT;

SELECT id,permission_name,permission_code,parent_id,permission_type,menu_scope
FROM sys_permission WHERE permission_code LIKE 'message:%' ORDER BY parent_id,sort,id;
