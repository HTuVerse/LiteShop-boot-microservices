-- MySQL 8：先执行此脚本，再重启用户服务并刷新前端。
-- 菜单表属于 liteshop_user，与 liteshop_config 网站配置表无关。
-- 维护窗口执行；仅补齐缺失范围和平台等级入口，不更改已有菜单启用状态或已撤销授权。
SET NAMES utf8mb4;
USE liteshop_user;
DROP PROCEDURE IF EXISTS migrate_menu_scope_20260904;
DELIMITER $$
CREATE PROCEDURE migrate_menu_scope_20260904()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_parent BIGINT;
  DECLARE v_menu BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sys_permission' AND column_name='menu_scope') THEN
    ALTER TABLE sys_permission ADD COLUMN menu_scope TINYINT UNSIGNED NULL
      COMMENT '导航显示范围：0平台和租户，1仅平台，2仅租户；按钮继承页面';
  END IF;
  IF EXISTS(SELECT 1 FROM sys_permission WHERE menu_scope IS NOT NULL
    AND BINARY CAST(menu_scope AS CHAR) NOT IN ('0','1','2','ALL','PLATFORM','TENANT')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='menu_scope包含未知值，请核对后再迁移。';
  END IF;
  -- 先将旧字符串映射为数字文本，再改变字段类型，避免MySQL把字符串隐式转为0。
  UPDATE sys_permission SET menu_scope=CASE BINARY CAST(menu_scope AS CHAR)
    WHEN 'ALL' THEN 0 WHEN 'PLATFORM' THEN 1 WHEN 'TENANT' THEN 2
    ELSE menu_scope END WHERE menu_scope IS NOT NULL;
  ALTER TABLE sys_permission MODIFY COLUMN menu_scope TINYINT UNSIGNED NULL
    COMMENT '导航显示范围：0平台和租户，1仅平台，2仅租户；按钮继承页面';
  START TRANSACTION;
  UPDATE sys_permission SET menu_scope=CASE
    WHEN component_address IN ('Layout','/index/index','index/index') THEN 0
    WHEN TRIM(TRAILING '/' FROM REPLACE(REPLACE(REPLACE(component_address,'.vue',''),'/src/views/',''),'src/views/',''))
      IN ('dashboard/console','dashboard/console/index','/dashboard/console','/dashboard/console/index',
          'system/user-center','system/user-center/index','/system/user-center','/system/user-center/index') THEN 0
    WHEN permission_code LIKE 'platform:%' OR component_address LIKE 'platform/%'
      OR component_address LIKE '/platform/%' THEN 1
    ELSE 2 END
  WHERE permission_type=1 AND menu_scope IS NULL;

  IF NOT EXISTS(SELECT 1 FROM sys_permission WHERE permission_code='platform:level-template') THEN
    IF EXISTS(SELECT 1 FROM sys_permission WHERE router_address='/platform/level-template') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='平台等级体系路由已被占用，请先核对菜单。';
    END IF;
    SELECT MIN(id) INTO v_parent FROM sys_permission
      WHERE component_address IN ('Layout','/index/index','index/index')
        AND (permission_code='platform:menu' OR router_address='/platform');
    -- 没有平台目录时使用顶层入口，可在菜单管理中移动到目录。
    SELECT COALESCE(MAX(id),0)+1 INTO v_menu FROM sys_permission;
    INSERT INTO sys_permission(id,permission_name,permission_code,parent_id,permission_type,
      component_address,router_address,menu_scope,sort,status,icon,is_hide,is_iframe,keep_alive,create_time,update_time)
      VALUES(v_menu,'平台等级体系','platform:level-template',COALESCE(v_parent,0),1,
        'platform/level-template/index','/platform/level-template',1,25,1,'ri:vip-crown-line',0,0,0,NOW(),NOW());
    INSERT INTO sys_role_permission(tenant_id,role_id,permission_id)
      SELECT NULL,id,v_menu FROM sys_role WHERE tenant_id IS NULL AND role_code='SUPER_ADMIN';
  END IF;
  COMMIT;
END$$
DELIMITER ;
CALL migrate_menu_scope_20260904();
DROP PROCEDURE IF EXISTS migrate_menu_scope_20260904;

-- 结果核对：已有菜单如果被禁用、隐藏或撤销授权，需按实际意图在管理界面恢复。
SELECT p.id,p.permission_name,p.permission_code,p.menu_scope,p.status,p.is_hide,p.parent_id,
  EXISTS(SELECT 1 FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id
    WHERE rp.permission_id=p.id AND r.role_code='SUPER_ADMIN' AND r.tenant_id IS NULL) AS super_admin_granted
FROM sys_permission p WHERE p.permission_code IN ('platform:level-template','system:level');
