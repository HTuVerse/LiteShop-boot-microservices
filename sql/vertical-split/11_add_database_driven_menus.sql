-- 将原先由前端硬编码的入口补入全局菜单表。请由数据库管理员手动执行整个文件。
-- 已有菜单的名称、层级、路由、状态和已有角色授权保持不变。
-- 仅新插入的记录授予现有平台 SUPER_ADMIN / 商户 ADMIN；
-- 工作台和个人中心另授予 STORE_ADMIN / STORE_STAFF。
-- 普通菜单后续由“角色管理”分配。禁用/删除/取消授权后前端不再补回。
SET NAMES utf8mb4;
USE liteshop_user;

DROP PROCEDURE IF EXISTS add_database_menus_20260903;
DELIMITER $$
CREATE PROCEDURE add_database_menus_20260903()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION, SQLWARNING
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS tmp_liteshop_menu_seed;
    DROP TEMPORARY TABLE IF EXISTS tmp_liteshop_menu_added;
    RESIGNAL;
  END;

  DROP TEMPORARY TABLE IF EXISTS tmp_liteshop_menu_seed;
  CREATE TEMPORARY TABLE tmp_liteshop_menu_seed (
    id BIGINT PRIMARY KEY, title VARCHAR(100), code VARCHAR(100),
    parent_code VARCHAR(100), component VARCHAR(255), route VARCHAR(255),
    sort INT, icon VARCHAR(255), hidden TINYINT DEFAULT 0
  );
  INSERT INTO tmp_liteshop_menu_seed VALUES
    (60, '工作台', 'common:console', '', 'dashboard/console/index', '/dashboard/console', 1, 'ri:dashboard-line', 0),
    (61, '个人中心', 'common:profile', '', 'system/user-center/index', '/system/user-center', 99, 'ri:user-line', 1),
    (86, 'AI店长', 'ai:manager:menu', '', 'Layout', '/ai-manager', 55, 'ri:robot-line', 0),
    (24, '会员等级', 'system:level', 'system:menu', 'system/level/index', '/system/level', 24, 'ri:vip-crown-line', 0),
    (70, '运营工具', 'operations:menu', '', 'Layout', '/operations', 60, 'ri:store-2-line', 0),
    (71, '直接收银', 'operations:direct-cashier', 'operations:menu', 'cashier/workspace/index', '/operations/direct-cashier', 10, 'ri:cash-line', 0),
    (72, '餐桌收银', 'operations:table-cashier', 'operations:menu', 'cashier/tableCashier/index', '/operations/table-cashier', 20, 'ri:restaurant-line', 0),
    (74, '核销记录', 'operations:verify-record', 'operations:menu', 'activity/record/index', '/operations/verify-record', 40, 'ri:coupon-line', 0),
    (75, '打印模板', 'operations:templates', 'operations:menu', 'device/template/index', '/operations/templates', 50, 'ri:printer-line', 0),
    (76, '经营洞察', 'operations:assistant', 'ai:manager:menu', 'operations/assistant/index', '/operations/assistant', 10, 'ri:sparkling-line', 0),
    (77, '经营顾问', 'operations:assistant:advisor', 'ai:manager:menu', 'operations/advisor/index', '/operations/advisor', 20, 'ri:chat-ai-line', 0);

  START TRANSACTION;
  -- 同权限编码视为已有配置，不覆盖、不重新授予已撤销的角色权限。
  DELETE s FROM tmp_liteshop_menu_seed s
    INNER JOIN sys_permission p ON p.permission_code = s.code;
  SELECT COUNT(*) INTO v_count FROM tmp_liteshop_menu_seed s
    INNER JOIN sys_permission p ON p.id = s.id OR p.router_address = s.route;
  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '菜单补丁停止：预置ID或路由已被其他菜单占用，请核对后调整脚本。';
  END IF;

  CREATE TEMPORARY TABLE tmp_liteshop_menu_added (id BIGINT PRIMARY KEY);
  INSERT INTO tmp_liteshop_menu_added SELECT id FROM tmp_liteshop_menu_seed;

  INSERT INTO sys_permission
    (id, permission_name, permission_code, parent_id, permission_type, component_address,
     router_address, sort, status, icon, is_hide, is_iframe, keep_alive, create_time, update_time)
  SELECT id, title, code, 0, 1, component, route, sort, 1, icon, hidden, 0, 0, NOW(), NOW()
  FROM tmp_liteshop_menu_seed WHERE parent_code = '';

  SELECT COUNT(*) INTO v_count FROM tmp_liteshop_menu_seed s
    LEFT JOIN sys_permission p ON p.permission_code = s.parent_code
    WHERE s.parent_code <> '' AND
      (p.id IS NULL OR p.permission_type <> 1 OR
       p.component_address NOT IN ('Layout', '/index/index', 'index/index', 'index/index.vue'));
  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '菜单补丁停止：缺少有效的系统管理或运营工具目录，请先核对目录。';
  END IF;

  INSERT INTO sys_permission
    (id, permission_name, permission_code, parent_id, permission_type, component_address,
     router_address, sort, status, icon, is_hide, is_iframe, keep_alive, create_time, update_time)
  SELECT s.id, s.title, s.code, p.id, 1, s.component, s.route, s.sort, 1,
    s.icon, s.hidden, 0, 0, NOW(), NOW()
  FROM tmp_liteshop_menu_seed s
    INNER JOIN sys_permission p ON p.permission_code = s.parent_code
  WHERE s.parent_code <> '';

  INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
  SELECT r.tenant_id, r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
    INNER JOIN tmp_liteshop_menu_added a ON a.id = p.id
  WHERE ((r.tenant_id IS NULL AND r.role_code = 'SUPER_ADMIN')
    OR (r.tenant_id IS NOT NULL AND r.role_code = 'ADMIN')
    OR (r.tenant_id IS NOT NULL AND r.role_code IN ('STORE_ADMIN', 'STORE_STAFF')
        AND p.permission_code IN ('common:console', 'common:profile')))
    AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);
  COMMIT;
  SELECT p.id, p.permission_name, p.parent_id, p.router_address, p.status
    FROM sys_permission p INNER JOIN tmp_liteshop_menu_added a ON a.id = p.id
    ORDER BY p.sort, p.id;
  DROP TEMPORARY TABLE tmp_liteshop_menu_seed;
  DROP TEMPORARY TABLE tmp_liteshop_menu_added;
END$$
DELIMITER ;
CALL add_database_menus_20260903();
DROP PROCEDURE IF EXISTS add_database_menus_20260903;
-- 执行后刷新前端页面。新建数据库先执行 01~08，再执行本文件。
-- 现有库已执行过 08 时不要重复执行 08；本文件可重跑，已有权限编码会跳过。

