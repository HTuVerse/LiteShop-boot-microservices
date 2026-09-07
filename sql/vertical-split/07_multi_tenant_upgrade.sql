-- ============================================================
-- 历史环境升级脚本：空库初始化禁止执行，请使用 01~06 + 08。
-- 已部署数据库的多租户升级脚本
-- 执行前请备份。存量业务数据统一归属租户 1；平台超级管理员需在最后单独置空 tenant_id。
-- ============================================================
USE `liteshop_user`;

-- 先执行以下检查；任一查询返回记录时，先合并/修正重复值再继续 ALTER TABLE。
SELECT `open_id`, COUNT(*) AS c FROM `sys_user`
WHERE `open_id` IS NOT NULL GROUP BY `tenant_id`, `open_id` HAVING c > 1;
SELECT `phone`, COUNT(*) AS c FROM `sys_user`
WHERE `phone` IS NOT NULL GROUP BY `tenant_id`, `phone` HAVING c > 1;
SELECT `email`, COUNT(*) AS c FROM `sys_user`
WHERE `email` IS NOT NULL GROUP BY `tenant_id`, `email` HAVING c > 1;

ALTER TABLE `sys_tenant`
  ADD COLUMN `tenant_code` varchar(50) NULL COMMENT '租户编码（登录与外部标识使用）' AFTER `id`;
UPDATE `sys_tenant` SET `tenant_code` = CONCAT('TENANT_', `id`) WHERE `tenant_code` IS NULL;
UPDATE `sys_tenant` SET `tenant_code` = 'DEFAULT' WHERE `id` = 1;
ALTER TABLE `sys_tenant`
  MODIFY COLUMN `tenant_code` varchar(50) NOT NULL COMMENT '租户编码（登录与外部标识使用）',
  ADD UNIQUE INDEX `uk_tenant_code` (`tenant_code`);

ALTER TABLE `sys_user`
  MODIFY COLUMN `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID；平台超级管理员为NULL',
  ADD UNIQUE INDEX `uk_tenant_open_id` (`tenant_id`, `open_id`),
  ADD UNIQUE INDEX `uk_tenant_phone` (`tenant_id`, `phone`),
  ADD UNIQUE INDEX `uk_tenant_email` (`tenant_id`, `email`);

ALTER TABLE `sys_role`
  MODIFY COLUMN `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID；平台内置角色为NULL';

ALTER TABLE `sys_user_role`
  ADD COLUMN `tenant_id` bigint NULL DEFAULT 1 COMMENT '租户ID；平台用户为NULL' FIRST,
  ADD INDEX `idx_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `sys_role_permission`
  ADD COLUMN `tenant_id` bigint NULL DEFAULT 1 COMMENT '租户ID；平台角色为NULL' FIRST,
  ADD INDEX `idx_tenant_role` (`tenant_id`, `role_id`);

CREATE TABLE IF NOT EXISTS `sys_user_store` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认门店：0-否 1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_tenant_user_store` (`tenant_id`, `user_id`, `store_id`),
  INDEX `idx_tenant_user` (`tenant_id`, `user_id`),
  INDEX `idx_tenant_store` (`tenant_id`, `store_id`)
) ENGINE=InnoDB COMMENT='用户可管理门店关联表';

-- 关联表回填租户
UPDATE `sys_user_role` ur
JOIN `sys_user` u ON u.id = ur.user_id
SET ur.tenant_id = u.tenant_id;

UPDATE `sys_role_permission` rp
JOIN `sys_role` r ON r.id = rp.role_id
SET rp.tenant_id = r.tenant_id;

-- 平台超级管理员不属于任何租户；根据角色关系识别。
UPDATE `sys_user` u
JOIN `sys_user_role` ur ON ur.user_id = u.id
JOIN `sys_role` r ON r.id = ur.role_id AND r.role_code = 'SUPER_ADMIN'
SET u.tenant_id = NULL, ur.tenant_id = NULL, r.tenant_id = NULL;

UPDATE `sys_role_permission` rp
JOIN `sys_role` r ON r.id = rp.role_id AND r.role_code = 'SUPER_ADMIN'
SET rp.tenant_id = NULL;
