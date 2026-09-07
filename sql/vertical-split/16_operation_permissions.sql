-- 操作权限升级。先执行 01~15 对应结构及菜单补丁，再执行本文件。
-- 在发布新版后端之前执行；首次为原来拥有菜单的角色保留其原有操作能力。
-- 仅给本次新增权限补齐旧授权，重跑不会恢复已撤销的角色操作权限。
USE liteshop_user;
DROP PROCEDURE IF EXISTS migrate_operation_permissions_20260904;
DELIMITER $$
CREATE PROCEDURE migrate_operation_permissions_20260904()
BEGIN
  DECLARE conflicts INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS tmp_operation_seed;
    DROP TEMPORARY TABLE IF EXISTS tmp_operation_grants;
    RESIGNAL;
  END;
  CREATE TEMPORARY TABLE tmp_operation_seed (
    id BIGINT PRIMARY KEY, code VARCHAR(100), parent_code VARCHAR(100), title VARCHAR(100), sort INT
  );
  INSERT INTO tmp_operation_seed VALUES
    (900000, 'config:banner:add', 'config:banner', '新增', 1),
    (900001, 'config:banner:delete', 'config:banner', '删除', 2),
    (900002, 'config:banner:edit', 'config:banner', '编辑', 3),
    (900003, 'config:banner:status', 'config:banner', '调整状态', 4),
    (900004, 'config:basic:add', 'config:basic', '新增', 5),
    (900005, 'config:basic:delete', 'config:basic', '删除', 6),
    (900006, 'config:basic:edit', 'config:basic', '编辑', 7),
    (900007, 'config:basic:refresh', 'config:basic', '刷新配置缓存', 8),
    (900008, 'marketing:coupon:add', 'marketing:coupon', '新增', 9),
    (900009, 'marketing:coupon:delete', 'marketing:coupon', '删除', 10),
    (900010, 'marketing:coupon:edit', 'marketing:coupon', '编辑', 11),
    (900011, 'marketing:coupon:send', 'marketing:coupon', '发放优惠券', 12),
    (900012, 'marketing:coupon:status', 'marketing:coupon', '调整状态', 13),
    (900013, 'marketing:coupon:verify', 'marketing:coupon', '核销优惠券', 14),
    (900014, 'marketing:lottery:add', 'marketing:lottery', '新增', 15),
    (900015, 'marketing:lottery:chance', 'marketing:lottery', '调整抽奖次数', 16),
    (900016, 'marketing:lottery:clear-records', 'marketing:lottery', '清空抽奖记录', 17),
    (900017, 'marketing:lottery:delete', 'marketing:lottery', '删除', 18),
    (900018, 'marketing:lottery:edit', 'marketing:lottery', '编辑', 19),
    (900019, 'marketing:lottery:prize', 'marketing:lottery', '维护奖品', 20),
    (900020, 'marketing:lottery:status', 'marketing:lottery', '调整状态', 21),
    (900021, 'marketing:lottery:stock', 'marketing:lottery', '调整奖品库存', 22),
    (900032, 'operations:direct-cashier:add-items', 'operations:direct-cashier', '提交订单 / 加菜', 33),
    (900033, 'operations:direct-cashier:cancel', 'operations:direct-cashier', '取消订单', 34),
    (900034, 'operations:direct-cashier:delete', 'operations:direct-cashier', '删除', 35),
    (900035, 'operations:direct-cashier:discount', 'operations:direct-cashier', '收银折扣', 36),
    (900036, 'operations:direct-cashier:gift', 'operations:direct-cashier', '赠菜', 37),
    (900037, 'operations:direct-cashier:print', 'operations:direct-cashier', '打印', 38),
    (900038, 'operations:direct-cashier:refund', 'operations:direct-cashier', '退菜', 39),
    (900039, 'operations:direct-cashier:settle', 'operations:direct-cashier', '结算收款', 40),
    (900040, 'operations:direct-cashier:status', 'operations:direct-cashier', '调整状态', 41),
    (900041, 'operations:direct-cashier:verify', 'operations:direct-cashier', '核销优惠券', 42),
    (900042, 'operations:table-cashier:add-items', 'operations:table-cashier', '提交订单 / 加菜', 43),
    (900043, 'operations:table-cashier:cancel', 'operations:table-cashier', '取消订单', 44),
    (900044, 'operations:table-cashier:clear', 'operations:table-cashier', '清空会话', 45),
    (900045, 'operations:table-cashier:delete', 'operations:table-cashier', '删除', 46),
    (900046, 'operations:table-cashier:discount', 'operations:table-cashier', '收银折扣', 47),
    (900047, 'operations:table-cashier:gift', 'operations:table-cashier', '赠菜', 48),
    (900048, 'operations:table-cashier:open', 'operations:table-cashier', '开桌', 49),
    (900049, 'operations:table-cashier:print', 'operations:table-cashier', '打印', 50),
    (900050, 'operations:table-cashier:refund', 'operations:table-cashier', '退菜', 51),
    (900051, 'operations:table-cashier:settle', 'operations:table-cashier', '结算收款', 52),
    (900052, 'operations:table-cashier:status', 'operations:table-cashier', '调整状态', 53),
    (900053, 'operations:table-cashier:verify', 'operations:table-cashier', '核销优惠券', 54),
    (900054, 'operations:templates:add', 'operations:templates', '新增', 55),
    (900055, 'operations:templates:default', 'operations:templates', '设置默认模板', 56),
    (900056, 'operations:templates:delete', 'operations:templates', '删除', 57),
    (900057, 'operations:templates:edit', 'operations:templates', '编辑', 58),
    (900058, 'operations:templates:status', 'operations:templates', '调整状态', 59),
    (900059, 'operations:verify-record:verify', 'operations:verify-record', '核销优惠券', 60),
    (900060, 'restaurant:category:add', 'restaurant:category', '新增', 61),
    (900061, 'restaurant:category:delete', 'restaurant:category', '删除', 62),
    (900062, 'restaurant:category:edit', 'restaurant:category', '编辑', 63),
    (900063, 'restaurant:dish:add', 'restaurant:dish', '新增', 64),
    (900064, 'restaurant:dish:delete', 'restaurant:dish', '删除', 65),
    (900065, 'restaurant:dish:edit', 'restaurant:dish', '编辑', 66),
    (900066, 'restaurant:dish:spec', 'restaurant:dish', '维护菜品规格', 67),
    (900067, 'restaurant:order:add-items', 'restaurant:order', '提交订单 / 加菜', 68),
    (900068, 'restaurant:order:cancel', 'restaurant:order', '取消订单', 69),
    (900069, 'restaurant:order:delete', 'restaurant:order', '删除', 70),
    (900070, 'restaurant:order:discount', 'restaurant:order', '收银折扣', 71),
    (900071, 'restaurant:order:gift', 'restaurant:order', '赠菜', 72),
    (900072, 'restaurant:order:print', 'restaurant:order', '打印', 73),
    (900073, 'restaurant:order:refund', 'restaurant:order', '退菜', 74),
    (900074, 'restaurant:order:settle', 'restaurant:order', '结算收款', 75),
    (900075, 'restaurant:order:status', 'restaurant:order', '调整状态', 76),
    (900076, 'restaurant:printer:add', 'restaurant:printer', '新增', 77),
    (900077, 'restaurant:printer:delete', 'restaurant:printer', '删除', 78),
    (900078, 'restaurant:printer:edit', 'restaurant:printer', '编辑', 79),
    (900079, 'restaurant:printer:print', 'restaurant:printer', '打印', 80),
    (900080, 'restaurant:printer:status', 'restaurant:printer', '调整状态', 81),
    (900081, 'restaurant:printer:sync', 'restaurant:printer', '同步设备状态', 82),
    (900082, 'restaurant:store:add', 'restaurant:store', '新增', 83),
    (900083, 'restaurant:store:delete', 'restaurant:store', '删除', 84),
    (900084, 'restaurant:store:edit', 'restaurant:store', '编辑', 85),
    (900085, 'restaurant:store:status', 'restaurant:store', '调整状态', 86),
    (900086, 'restaurant:table:add', 'restaurant:table', '新增', 87),
    (900087, 'restaurant:table:clear', 'restaurant:table', '清空会话', 88),
    (900088, 'restaurant:table:delete', 'restaurant:table', '删除', 89),
    (900089, 'restaurant:table:edit', 'restaurant:table', '编辑', 90),
    (900090, 'restaurant:table:open', 'restaurant:table', '开桌', 91),
    (900091, 'restaurant:table:settle', 'restaurant:table', '结算收款', 92),
    (900092, 'restaurant:table:status', 'restaurant:table', '调整状态', 93),
    (900093, 'system:level:add', 'system:level', '新增', 94),
    (900094, 'system:level:delete', 'system:level', '删除', 95),
    (900095, 'system:level:edit', 'system:level', '编辑', 96),
    (900096, 'system:level:scheme-activate', 'system:level', '启用等级方案', 97),
    (900097, 'system:level:scheme-add', 'system:level', '新建 / 引用等级方案', 98),
    (900098, 'system:level:scheme-delete', 'system:level', '删除等级方案', 99),
    (900099, 'system:level:scheme-edit', 'system:level', '编辑等级方案', 100),
    (900100, 'system:level:status', 'system:level', '调整状态', 101),
    (900101, 'system:role:add', 'system:role', '新增', 102),
    (900102, 'system:role:assign', 'system:role', '分配权限', 103),
    (900103, 'system:role:delete', 'system:role', '删除', 104),
    (900104, 'system:role:edit', 'system:role', '编辑', 105),
    (900105, 'system:user-store:assign', 'system:user-store', '分配权限', 106),
    (900106, 'system:user:add', 'system:user', '新增', 107),
    (900107, 'system:user:assign-role', 'system:user', '分配用户角色', 108),
    (900108, 'system:user:delete', 'system:user', '删除', 109),
    (900109, 'system:user:edit', 'system:user', '编辑', 110),
    (900110, 'system:user:level', 'system:user', '调整会员等级', 111),
    (900111, 'system:user:points-delete', 'system:user', '删除积分记录', 112),
    (900112, 'system:user:recharge', 'system:user', '余额充值', 113),
    (900113, 'system:user:reset-password', 'system:user', '重置密码', 114);
  START TRANSACTION;
  SELECT COUNT(*) INTO conflicts FROM tmp_operation_seed s
    LEFT JOIN sys_permission p ON p.permission_code = s.parent_code
    WHERE p.id IS NULL OR p.permission_type <> 1;
  IF conflicts > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少父菜单，请先补齐菜单数据后重试';
  END IF;
  SELECT COUNT(*) INTO conflicts FROM tmp_operation_seed s
    INNER JOIN sys_permission p ON p.permission_code = s.code
    INNER JOIN sys_permission parent ON parent.permission_code = s.parent_code
    WHERE p.permission_type <> 2 OR p.parent_id <> parent.id;
  IF conflicts > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已有同编码权限的类型或上级不一致，请先核对';
  END IF;
  DELETE s FROM tmp_operation_seed s INNER JOIN sys_permission p ON p.permission_code = s.code;
  SELECT COUNT(*) INTO conflicts FROM tmp_operation_seed s INNER JOIN sys_permission p ON p.id = s.id;
  IF conflicts > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '操作权限预置ID已被占用，请调整新增权限ID后重试';
  END IF;
  INSERT INTO sys_permission (id, permission_name, permission_code, parent_id, permission_type,
    component_address, router_address, sort, status, is_hide, is_iframe, keep_alive)
  SELECT s.id, s.title, s.code, p.id, 2, NULL, NULL, s.sort, 1, 0, 0, 0
    FROM tmp_operation_seed s INNER JOIN sys_permission p ON p.permission_code = s.parent_code;
  -- Materialize before inserting to avoid reading the target table in a NOT EXISTS subquery.
  CREATE TEMPORARY TABLE tmp_operation_grants AS
  SELECT DISTINCT rp.tenant_id, rp.role_id, s.id AS permission_id
    FROM tmp_operation_seed s INNER JOIN sys_permission p ON p.permission_code = s.parent_code
    INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
    INNER JOIN sys_role r ON r.id = rp.role_id
    WHERE p.status = 1 AND r.role_code <> 'USER';
  INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
    SELECT tenant_id, role_id, permission_id FROM tmp_operation_grants;
  COMMIT;
  SELECT COUNT(*) AS added_operation_permissions FROM tmp_operation_seed;
  DROP TEMPORARY TABLE tmp_operation_seed;
  DROP TEMPORARY TABLE tmp_operation_grants;
END$$
DELIMITER ;
CALL migrate_operation_permissions_20260904();
DROP PROCEDURE migrate_operation_permissions_20260904;
