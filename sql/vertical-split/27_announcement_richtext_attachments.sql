-- 公告富文本与附件增量升级。
-- 适用于已经执行过 26_message_announcements.sql 的环境；可重复执行。
SET NAMES utf8mb4;
USE `liteshop_message`;

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

ALTER TABLE `sys_announcement`
  MODIFY COLUMN `content` MEDIUMTEXT NOT NULL COMMENT '富文本公告内容';
