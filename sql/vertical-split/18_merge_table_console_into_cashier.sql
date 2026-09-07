-- 合并重复的“餐台控制台”与“餐桌收银”入口。
-- 已有餐台控制台授权会等价迁移到餐桌收银，再删除旧菜单及其操作权限。
-- 可重复执行；新版前后端发布前执行，执行后用户需刷新页面。
SET NAMES utf8mb4;
USE liteshop_user;

DROP PROCEDURE IF EXISTS merge_table_console_into_cashier_20260905;
DELIMITER $$
CREATE PROCEDURE merge_table_console_into_cashier_20260905()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_console_id BIGINT DEFAULT NULL;
  DECLARE v_cashier_id BIGINT DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT MIN(id) INTO v_console_id
    FROM sys_permission WHERE permission_code = 'operations:console';
  SELECT MIN(id) INTO v_cashier_id
    FROM sys_permission WHERE permission_code = 'operations:table-cashier';

  IF v_console_id IS NOT NULL AND v_cashier_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '合并菜单失败：餐桌收银菜单不存在，请先执行菜单补丁。';
  END IF;

  IF v_console_id IS NOT NULL THEN
    START TRANSACTION;

    -- 将旧菜单及同名操作授权映射到餐桌收银，避免原有角色丢失权限。
    INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
    SELECT rp.tenant_id, rp.role_id, target.id
      FROM sys_role_permission rp
      INNER JOIN sys_permission source ON source.id = rp.permission_id
      INNER JOIN sys_permission target ON target.permission_code = CASE
        WHEN source.permission_code = 'operations:console'
          THEN 'operations:table-cashier'
        ELSE CONCAT(
          'operations:table-cashier',
          SUBSTRING(source.permission_code, CHAR_LENGTH('operations:console') + 1)
        )
      END
      WHERE source.permission_code = 'operations:console'
         OR source.permission_code LIKE 'operations:console:%';

    DELETE rp FROM sys_role_permission rp
      INNER JOIN sys_permission p ON p.id = rp.permission_id
      WHERE p.permission_code = 'operations:console'
         OR p.permission_code LIKE 'operations:console:%';
    DELETE FROM sys_permission
      WHERE permission_code LIKE 'operations:console:%';
    DELETE FROM sys_permission
      WHERE permission_code = 'operations:console';

    COMMIT;
  END IF;
END$$
DELIMITER ;
CALL merge_table_console_into_cashier_20260905();
DROP PROCEDURE IF EXISTS merge_table_console_into_cashier_20260905;

SELECT permission_name, permission_code, component_address, router_address
  FROM sys_permission
  WHERE permission_code IN ('operations:table-cashier', 'operations:console')
     OR permission_code LIKE 'operations:console:%'
  ORDER BY permission_code;
