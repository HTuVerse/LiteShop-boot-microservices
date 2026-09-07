-- 为已初始化的微服务数据库补充“菜单管理”入口。
-- 适用：已经执行过 01~08，且 sys_permission 中尚无 platform:permission。
-- 本文件只新增一个全局菜单定义，并将其授予已有的 SUPER_ADMIN 角色。
-- 请执行整个脚本；脚本不会更新或覆盖 ID/权限编码冲突的数据。

SET NAMES utf8mb4;
USE `liteshop_user`;

DROP PROCEDURE IF EXISTS `add_permission_management_menu_20260903`;
DELIMITER $$
CREATE PROCEDURE `add_permission_management_menu_20260903`()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION, SQLWARNING
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;
  SELECT COUNT(*) INTO v_count
  FROM sys_permission
  WHERE id = 12 OR permission_code = 'platform:permission';
  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '菜单补丁停止：ID 12 或 platform:permission 已存在，请先核对该记录。';
  END IF;

  SELECT COUNT(*) INTO v_count
  FROM sys_permission
  WHERE id = 10 AND permission_code = 'platform:menu' AND permission_type = 1;
  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '菜单补丁停止：未找到 ID 10 的平台管理根菜单。';
  END IF;

  INSERT INTO sys_permission
    (id, permission_name, permission_code, parent_id, permission_type,
     component_address, router_address, sort, status, icon, link,
     is_hide, is_iframe, keep_alive)
  VALUES
    (12, '菜单管理', 'platform:permission', 10, 1,
     'system/menu/index', '/platform/permission', 12, 1, 'Menu', NULL,
     0, 0, 1);

  INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
  SELECT NULL, r.id, 12
  FROM sys_role r
  WHERE r.tenant_id IS NULL AND r.role_code = 'SUPER_ADMIN'
    AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = 12
    );
  COMMIT;
END$$
DELIMITER ;

CALL `add_permission_management_menu_20260903`();
DROP PROCEDURE IF EXISTS `add_permission_management_menu_20260903`;

SELECT id, permission_name, permission_code, parent_id, permission_type,
       component_address, router_address, status
FROM sys_permission
WHERE permission_code = 'platform:permission';

-- 成功后请退出后台并重新登录，以重新加载动态菜单。
