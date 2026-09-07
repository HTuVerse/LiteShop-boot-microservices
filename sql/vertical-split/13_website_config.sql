-- 网站配置增量迁移。可重复执行，不覆盖现有网站配置或菜单。
-- 先执行本脚本，再部署 config-service 与 user-service。
SET NAMES utf8mb4;
USE `liteshop_config`;

CREATE TABLE IF NOT EXISTS `sys_website_config` (
  `id` tinyint NOT NULL COMMENT '单例 ID，固定为 1',
  `config_value` json NOT NULL COMMENT '类型化网站配置 JSON，含 revision',
  `revision` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `updated_by` bigint NULL COMMENT '最后发布的超级管理员 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_website_singleton` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端全局网站配置（不属于任何商户）';

INSERT INTO `sys_website_config` (`id`, `config_value`, `revision`)
SELECT 1, JSON_OBJECT(
  'revision', 0, 'systemName', 'LiteShop', 'description', '商户运营管理平台',
  'watermarkEnabled', CAST('false' AS JSON), 'watermarkMode', 'username', 'watermarkText', '',
  'loginTitle', '欢迎回来', 'loginDescription', '使用已开通管理权限的账号登录。',
  'defaultLanguage', 'zh', 'captchaEnabled', CAST('false' AS JSON), 'captchaType', 'image',
  'maxLoginAttempts', NULL, 'lockMinutes', 10, 'maintenanceEnabled', CAST('false' AS JSON),
  'maintenanceMessage', '系统正在维护，请稍后再试。',
  'seoTitle', '', 'seoKeywords', '', 'seoDescription', '',
  'contactEmail', '', 'contactPhone', '', 'contactAddress', '',
  'copyright', '', 'icpNumber', '', 'publicSecurityNumber', ''
), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_website_config` WHERE `id` = 1);

USE `liteshop_user`;
-- 使用独立高位 ID；若该 ID 意外冲突，让主键约束报错，不覆盖其他菜单。
INSERT INTO `sys_permission`
  (`id`, `permission_name`, `permission_code`, `parent_id`, `permission_type`,
   `component_address`, `router_address`, `sort`, `status`, `icon`, `is_hide`, `is_iframe`, `keep_alive`)
SELECT 202609040001, '网站配置', 'platform:website',
  COALESCE((SELECT `id` FROM `sys_permission` WHERE `permission_code` = 'platform:menu' LIMIT 1), 0),
  1, 'config/website/index', '/platform/website', 13, 1, 'ri:global-line', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'platform:website');

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r
JOIN `sys_permission` p ON p.permission_code = 'platform:website'
WHERE r.role_code = 'SUPER_ADMIN'
AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
