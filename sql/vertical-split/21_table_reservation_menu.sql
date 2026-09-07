-- 预约管理菜单增量迁移。可重复执行，不覆盖已有菜单或重新授予已撤销权限。
-- 仅当本脚本首次创建菜单时，授权现有平台 SUPER_ADMIN 与商户 ADMIN。
SET NAMES utf8mb4;
USE `liteshop_user`;

DROP PROCEDURE IF EXISTS add_table_reservation_menu_20260906;
DELIMITER $$
CREATE PROCEDURE add_table_reservation_menu_20260906()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_parent_id BIGINT DEFAULT NULL;
  DECLARE v_menu_id BIGINT DEFAULT 202609060001;
  DECLARE v_existing_id BIGINT DEFAULT NULL;
  DECLARE v_conflict_count INT DEFAULT 0;
  DECLARE v_inserted TINYINT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION, SQLWARNING
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  SELECT id INTO v_existing_id
  FROM sys_permission
  WHERE permission_code = 'operations:table-reservation'
  LIMIT 1;

  IF v_existing_id IS NULL THEN
    SELECT id INTO v_parent_id
    FROM sys_permission
    WHERE permission_code = 'operations:menu'
      AND permission_type = 1
    LIMIT 1;

    IF v_parent_id IS NULL THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '预约管理菜单迁移停止：缺少有效的 operations:menu 目录。';
    END IF;

    SELECT COUNT(*) INTO v_conflict_count
    FROM sys_permission
    WHERE id = v_menu_id
       OR router_address = '/operations/table-reservation';

    IF v_conflict_count > 0 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '预约管理菜单迁移停止：预置 ID 或路由已被其他菜单占用。';
    END IF;

    INSERT INTO sys_permission
      (id, permission_name, permission_code, parent_id, permission_type,
       component_address, router_address, sort, status, icon,
       is_hide, is_iframe, keep_alive, create_time, update_time)
    VALUES
      (v_menu_id, '预约管理', 'operations:table-reservation', v_parent_id, 1,
       'cashier/tableReservation/index', '/operations/table-reservation', 30, 1,
       'ri:calendar-check-line', 0, 0, 0, NOW(), NOW());

    SET v_inserted = 1;
  END IF;

  IF v_inserted = 1 THEN
    INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
    SELECT r.tenant_id, r.id, v_menu_id
    FROM sys_role r
    WHERE ((r.tenant_id IS NULL AND r.role_code = 'SUPER_ADMIN')
        OR (r.tenant_id IS NOT NULL AND r.role_code = 'ADMIN'))
      AND NOT EXISTS (
        SELECT 1 FROM sys_role_permission rp
        WHERE rp.role_id = r.id AND rp.permission_id = v_menu_id
      );
  END IF;

  COMMIT;

  SELECT id, permission_name, permission_code, parent_id,
         component_address, router_address, sort, icon
  FROM sys_permission
  WHERE permission_code = 'operations:table-reservation';
END$$
DELIMITER ;

CALL add_table_reservation_menu_20260906();
DROP PROCEDURE IF EXISTS add_table_reservation_menu_20260906;
