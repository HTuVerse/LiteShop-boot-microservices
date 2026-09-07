-- MySQL 8.0，手动执行。先备份 liteshop_user，并暂停 user-service 写入。
-- 先执行尚未执行的 11_add_database_driven_menus.sql，随后执行本文件，再部署新版 user-service。
-- DDL 会隐式提交；失败时保留已完成的结构，修正报错后可重跑。迁移数据阶段单独使用事务。
-- 原等级保留为平台默认模板，为每个租户复制独立方案，不改已有会员的经验、等级、余额或积分。
SET NAMES utf8mb4;
USE liteshop_user;
CREATE TABLE IF NOT EXISTS user_level_scheme (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NULL COMMENT 'NULL=平台模板，其他=租户自有方案',
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NULL,
  source_template_id BIGINT NULL,
  is_active TINYINT NOT NULL DEFAULT 0,
  is_default TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  active_tenant_id BIGINT GENERATED ALWAYS AS (IF(is_active=1,tenant_id,NULL)) STORED,
  default_template_slot TINYINT GENERATED ALWAYS AS (IF(tenant_id IS NULL AND is_default=1,1,NULL)) STORED,
  UNIQUE KEY uk_active_tenant(active_tenant_id),
  UNIQUE KEY uk_default_template(default_template_slot),
  KEY idx_tenant_scheme(tenant_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS migrate_level_schemes_20260903;
DELIMITER $$
CREATE PROCEDURE migrate_level_schemes_20260903()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_template BIGINT;
  DECLARE v_level_max BIGINT DEFAULT 0;
  DECLARE v_scheme_max BIGINT DEFAULT 0;
  DECLARE v_new_levels BIGINT DEFAULT 0;
  DECLARE v_menu_id BIGINT;
  DECLARE v_parent BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION, SQLWARNING BEGIN ROLLBACK; RESIGNAL; END;

  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_level' AND column_name='tenant_id') THEN
    ALTER TABLE user_level ADD COLUMN tenant_id BIGINT NULL COMMENT 'NULL=平台模板';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_level' AND column_name='scheme_id') THEN
    ALTER TABLE user_level ADD COLUMN scheme_id BIGINT NULL COMMENT '等级方案ID';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='user_level' AND index_name='uk_level_code') THEN
    ALTER TABLE user_level DROP INDEX uk_level_code;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='user_level' AND index_name='uk_scheme_level') THEN
    ALTER TABLE user_level ADD UNIQUE KEY uk_scheme_level(scheme_id,level_code), ADD KEY idx_tenant_scheme_exp(tenant_id,scheme_id,status,required_exp);
  END IF;

  START TRANSACTION;
  SELECT id INTO v_template FROM user_level_scheme WHERE tenant_id IS NULL AND is_default=1 LIMIT 1;
  IF v_template IS NULL THEN
    SELECT COALESCE(MAX(id),0)+1 INTO v_template FROM user_level_scheme;
    INSERT INTO user_level_scheme(id,tenant_id,name,description,is_default)
      VALUES(v_template,NULL,'标准会员等级','由原全局等级迁移，商户引用后独立维护',1);
  END IF;
  UPDATE user_level SET scheme_id=v_template,tenant_id=NULL WHERE scheme_id IS NULL;
  IF EXISTS(SELECT 1 FROM user_level WHERE scheme_id=v_template AND (level_code<0 OR required_exp<0)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原等级存在负数编码或经验，请先修正后重跑。';
  END IF;
  IF EXISTS(SELECT required_exp FROM user_level WHERE scheme_id=v_template AND status=1 GROUP BY required_exp HAVING COUNT(*)>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='原启用等级有重复经验门槛，请先修正后重跑。';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM user_level WHERE scheme_id=v_template AND status=1 AND required_exp=0) THEN
    SELECT COALESCE(MAX(id),0) INTO v_level_max FROM user_level;
    INSERT INTO user_level(id,tenant_id,scheme_id,level_code,level_name,required_exp,status,sort)
      SELECT v_level_max+1,NULL,v_template,COALESCE(MAX(level_code),-1)+1,'普通会员',0,1,0
      FROM (SELECT level_code FROM user_level WHERE scheme_id=v_template) existing_levels;
  END IF;

  -- 只给还没有任何方案的租户初始化。重跑不会切换已选方案或重复复制等级。
  DROP TEMPORARY TABLE IF EXISTS tmp_level_tenants;
  CREATE TEMPORARY TABLE tmp_level_tenants(tenant_id BIGINT PRIMARY KEY,scheme_id BIGINT NOT NULL);
  SELECT COALESCE(MAX(id),0) INTO v_scheme_max FROM user_level_scheme;
  INSERT INTO tmp_level_tenants
    SELECT t.id,v_scheme_max+ROW_NUMBER() OVER(ORDER BY t.id)
    FROM sys_tenant t WHERE NOT EXISTS(SELECT 1 FROM user_level_scheme s WHERE s.tenant_id=t.id);
  INSERT INTO user_level_scheme(id,tenant_id,name,description,source_template_id,is_active)
    SELECT scheme_id,tenant_id,'默认会员等级','保留原等级配置',v_template,1 FROM tmp_level_tenants;

  SELECT COALESCE(MAX(id),0) INTO v_level_max FROM user_level;
  SELECT COUNT(*) INTO v_new_levels FROM tmp_level_tenants CROSS JOIN user_level l WHERE l.scheme_id=v_template;
  IF CAST(v_level_max AS DECIMAL(20,0))+v_new_levels >= 9223372036854775807 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='会员等级ID空间不足，迁移未执行。';
  END IF;
  INSERT INTO user_level(id,tenant_id,scheme_id,level_code,level_name,required_exp,icon,description,benefits,status,sort,create_time,update_time)
    SELECT v_level_max+ROW_NUMBER() OVER(ORDER BY t.tenant_id,l.level_code),t.tenant_id,t.scheme_id,
      l.level_code,l.level_name,l.required_exp,l.icon,l.description,l.benefits,l.status,l.sort,NOW(),NOW()
    FROM tmp_level_tenants t CROSS JOIN user_level l WHERE l.scheme_id=v_template;

  -- 平台模板维护入口仍由数据库菜单决定，已有同编码菜单不覆盖、不重授撤销权限。
  IF NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='platform:level-template') THEN
    SELECT id INTO v_parent FROM sys_permission WHERE permission_code='platform:menu' LIMIT 1;
    IF v_parent IS NULL THEN
      SELECT id INTO v_parent FROM sys_permission WHERE router_address='/platform' AND component_address='Layout' LIMIT 1;
    END IF;
    IF v_parent IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='缺少平台管理目录，请先完成平台菜单迁移。'; END IF;
    IF EXISTS(SELECT 1 FROM sys_permission WHERE router_address='/platform/level-template') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='平台等级模板路由已被占用，请核对后重跑。';
    END IF;
    SELECT COALESCE(MAX(id),0)+1 INTO v_menu_id FROM sys_permission;
    INSERT INTO sys_permission(id,permission_name,permission_code,parent_id,permission_type,component_address,router_address,sort,status,icon,is_hide,is_iframe,keep_alive,create_time,update_time)
      VALUES(v_menu_id,'会员等级模板','platform:level-template',v_parent,1,'system/level/index','/platform/level-template',25,1,'ri:vip-crown-line',0,0,0,NOW(),NOW());
    INSERT INTO sys_role_permission(tenant_id,role_id,permission_id)
      SELECT NULL,r.id,v_menu_id FROM sys_role r WHERE r.tenant_id IS NULL AND r.role_code='SUPER_ADMIN';
  END IF;
  DROP TEMPORARY TABLE tmp_level_tenants;
  COMMIT;
END$$
DELIMITER ;
CALL migrate_level_schemes_20260903();
DROP PROCEDURE IF EXISTS migrate_level_schemes_20260903;

-- 结果核对：每个租户有一套启用方案，等级记录均有方案ID。
SELECT s.id,s.tenant_id,s.name,s.is_active,s.is_default,COUNT(l.id) AS level_count
FROM user_level_scheme s LEFT JOIN user_level l ON l.scheme_id=s.id GROUP BY s.id ORDER BY s.tenant_id,s.id;
SELECT COUNT(*) AS levels_without_scheme FROM user_level WHERE scheme_id IS NULL;
