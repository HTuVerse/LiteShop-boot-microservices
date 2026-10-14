-- ============================================================
-- 当前功能完整模拟数据（MySQL 8.0，可重复执行）
-- 前置条件及空库初始化顺序见同目录 README.md。
-- 自动清理全部租户及其业务数据，并移除 superadmin 之外的平台用户，
-- 在同一事务内重新生成 CHAIN_TEST；失败回滚，平台配置、角色、权限和套餐目录保留。
-- 执行时停止测试环境业务服务和定时任务，避免并发写入。
-- 仅用于本地开发/联调，生产环境禁止执行。
--
-- 租户编码：CHAIN_TEST
-- 所有测试账号密码：Admin@123456
-- ============================================================

USE `liteshop_user`;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `seed_current_demo_20260921`;
DELIMITER $$
CREATE PROCEDURE `seed_current_demo_20260921`()
BEGIN
  DECLARE v_now datetime DEFAULT NOW();
  DECLARE v_done tinyint DEFAULT 0;
  DECLARE v_schema varchar(64);
  DECLARE v_table varchar(64);
  DECLARE v_day int DEFAULT 0;
  DECLARE v_store int;
  DECLARE v_slot int;
  DECLARE v_order_id bigint;
  DECLARE v_time datetime;
  DECLARE v_payment tinyint;
  DECLARE v_status tinyint;
  DECLARE v_price decimal(10,2);
  DECLARE v_cost decimal(10,2);
  DECLARE v_quantity int;
  DECLARE v_discount decimal(10,2);
  DECLARE v_campaign bigint;
  DECLARE tenant_tables CURSOR FOR
SELECT c.TABLE_SCHEMA, c.TABLE_NAME
FROM information_schema.COLUMNS c
         JOIN information_schema.TABLES t
              ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
                  AND t.TABLE_TYPE = 'BASE TABLE'
WHERE c.COLUMN_NAME = 'tenant_id'
  AND c.TABLE_SCHEMA IN ('liteshop_user', 'liteshop_restaurant', 'liteshop_coupon',
                         'liteshop_lottery', 'liteshop_file', 'liteshop_config', 'liteshop_message')
GROUP BY c.TABLE_SCHEMA, c.TABLE_NAME
ORDER BY CASE WHEN c.TABLE_SCHEMA = 'liteshop_user' THEN 1 ELSE 0 END,
         c.TABLE_SCHEMA, c.TABLE_NAME;
DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
BEGIN
ROLLBACK;
DROP TEMPORARY TABLE IF EXISTS tmp_current_demo_tenants;
    RESIGNAL;
END;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'liteshop_restaurant'
      AND TABLE_NAME = 'store_dish'
      AND COLUMN_NAME = 'config_version'
  ) OR NOT EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'liteshop_restaurant'
      AND TABLE_NAME = 'menu_change_record'
  ) OR NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'liteshop_restaurant'
      AND TABLE_NAME = 'dish'
      AND COLUMN_NAME = 'sale_price'
  ) OR NOT EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'liteshop_restaurant'
      AND TABLE_NAME = 'pricing_campaign'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '测试数据导入停止：菜单与定价结构不完整，初始化或升级顺序详见README。';
END IF;

  -- 全部前置条件在删除数据前检查，防止旧结构导致清理后无法导入。
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'liteshop_message'
                                                       AND TABLE_NAME IN ('sys_announcement','sys_announcement_target',
                                                                          'sys_announcement_attachment','sys_announcement_read')) <> 4
    OR (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'liteshop_restaurant'
      AND TABLE_NAME IN ('analysis_store_daily','analysis_store_dish_daily','analysis_refresh_queue',
        'order_dish_change_log','order_price_adjustment','table_reservation')) <> 6
    OR NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'liteshop_restaurant'
      AND TABLE_NAME = 'restaurant_order_item' AND COLUMN_NAME = 'unit_cost_snapshot')
    OR NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'liteshop_restaurant'
      AND TABLE_NAME = 'pricing_campaign_scope' AND COLUMN_NAME = 'store_group_id')
    OR NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'liteshop_user'
      AND TABLE_NAME = 'user_level_scheme')
    OR NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'liteshop_user'
      AND TABLE_NAME = 'sys_permission' AND COLUMN_NAME = 'menu_scope')
    OR NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'liteshop_user'
      AND TABLE_NAME = 'saas_ai_generation')
    OR NOT EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'liteshop_config'
      AND TABLE_NAME = 'tenant_payment_account')
    OR NOT EXISTS (SELECT 1 FROM liteshop_user.saas_plan_version v
      JOIN liteshop_user.saas_plan p ON p.id = v.plan_id
      JOIN liteshop_user.saas_plan_entitlement ai ON ai.plan_version_id = v.id AND ai.enabled = 1
      JOIN liteshop_user.saas_feature aif ON aif.id = ai.feature_id AND aif.feature_code = 'ai_manager'
      JOIN liteshop_user.saas_plan_entitlement calls ON calls.plan_version_id = v.id
        AND calls.enabled = 1 AND calls.quota_value > 0
      JOIN liteshop_user.saas_feature callsf ON callsf.id = calls.feature_id
        AND callsf.feature_code = 'ai_manager_calls'
      WHERE p.plan_code = 'pro' AND v.status = 'PUBLISHED')
    OR (SELECT COUNT(*) FROM liteshop_user.sys_permission WHERE status = 1
      AND permission_code IN ('restaurant:pricing','restaurant:tenant-analysis',
        'operations:assistant','operations:direct-cashier','operations:table-cashier',
        'config:payment-account')) <> 6 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '请先完成当前结构、会员、SaaS套餐和菜单初始化，详见README。';
END IF;

  IF NOT EXISTS (
    SELECT 1 FROM `liteshop_user`.`sys_user`
    WHERE `id` = 1 AND `username` = 'superadmin' AND `tenant_id` IS NULL
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '未找到平台超级管理员 superadmin，停止清理。';
END IF;

  DROP TEMPORARY TABLE IF EXISTS tmp_current_demo_tenants;
  CREATE TEMPORARY TABLE tmp_current_demo_tenants (
    tenant_id bigint NOT NULL PRIMARY KEY COMMENT '本次需要清理的全部租户'
  ) ENGINE=InnoDB;
START TRANSACTION;
INSERT INTO tmp_current_demo_tenants
SELECT id FROM sys_tenant;
-- 公告目标和附件没有tenant_id，必须在公告主表删除前按所属公告清理。
DELETE child FROM liteshop_message.sys_announcement_target child
    JOIN liteshop_message.sys_announcement a ON a.id=child.announcement_id
    JOIN tmp_current_demo_tenants t ON t.tenant_id=a.tenant_id;
  DELETE child FROM liteshop_message.sys_announcement_attachment child
    JOIN liteshop_message.sys_announcement a ON a.id=child.announcement_id
    JOIN tmp_current_demo_tenants t ON t.tenant_id=a.tenant_id;
  DELETE child FROM liteshop_message.sys_announcement_read child
    JOIN liteshop_message.sys_announcement a ON a.id=child.announcement_id
    JOIN tmp_current_demo_tenants t ON t.tenant_id=a.tenant_id;
OPEN tenant_tables;
cleanup_loop: LOOP
    FETCH tenant_tables INTO v_schema, v_table;
    IF v_done = 1 THEN LEAVE cleanup_loop; END IF;
    SET @current_demo_cleanup = CONCAT('DELETE target FROM `', REPLACE(v_schema,'`','``'),
      '`.`', REPLACE(v_table,'`','``'), '` target JOIN liteshop_user.tmp_current_demo_tenants selected',
      ' ON selected.tenant_id = target.tenant_id');
PREPARE current_demo_cleanup_stmt FROM @current_demo_cleanup;
EXECUTE current_demo_cleanup_stmt;
DEALLOCATE PREPARE current_demo_cleanup_stmt;
END LOOP;
CLOSE tenant_tables;
-- 订单删除触发器会重新写入刷新队列，因此在业务表删除后再清理一次。
DELETE q FROM liteshop_restaurant.analysis_refresh_queue q
    JOIN tmp_current_demo_tenants t ON t.tenant_id = q.tenant_id;
  DELETE t FROM sys_tenant t JOIN tmp_current_demo_tenants selected ON selected.tenant_id = t.id;

  -- 平台侧只保留内置超级管理员；先清理关联关系，再删除其余平台用户。
  DELETE ur FROM liteshop_user.sys_user_role ur
    JOIN liteshop_user.sys_user u ON u.id = ur.user_id
    WHERE u.tenant_id IS NULL AND NOT (u.id = 1 AND u.username = 'superadmin');
  DELETE FROM liteshop_user.sys_user
    WHERE tenant_id IS NULL AND NOT (id = 1 AND username = 'superadmin');

  -- 一、租户、角色、测试账号与门店数据权限
INSERT INTO `liteshop_user`.`sys_tenant`
(`id`, `tenant_code`, `tenant_name`, `contact_name`, `contact_phone`, `status`,
 `expire_time`, `remark`, `create_time`, `update_time`)
VALUES
    (920001, 'CHAIN_TEST', '星河食集连锁测试商户', '连锁测试负责人', '13992000000', 1,
     DATE_ADD(v_now, INTERVAL 365 DAY), 'CURRENT_DEMO_20260921：当前功能模拟租户', v_now, v_now);

INSERT INTO `liteshop_user`.`sys_role`
(`id`, `role_name`, `role_code`, `description`, `status`, `tenant_id`)
VALUES
    (920001101, '总部管理员', 'ADMIN', '管理整个连锁商户及全部门店', 1, 920001),
    (920001102, '门店管理员', 'STORE_ADMIN', '管理被授权门店并发起菜单变更', 1, 920001),
    (920001103, '门店员工', 'STORE_STAFF', '处理门店餐桌和订单', 1, 920001),
    (920001104, '普通会员', 'USER', '顾客测试账号', 1, 920001);

INSERT INTO `liteshop_user`.`sys_user`
(`id`, `username`, `password`, `nickname`, `gender`, `phone`, `email`, `introduction`,
 `exp`, `level`, `points`, `status`, `surplus_money`, `create_time`, `update_time`, `tenant_id`)
VALUES
    (920001001, 'chain_admin', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '星河总部管理员', 0, '13992000001', 'chain_admin@example.invalid', '总部菜单审批与批量发布测试账号', 0, 1, 0, 1, 0, v_now, v_now, 920001),
    (920001002, 'chain_flagship', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '旗舰店管理员', 0, '13992000002', 'chain_flagship@example.invalid', '旗舰店受限账号', 0, 1, 0, 1, 0, v_now, v_now, 920001),
    (920001003, 'chain_airport', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '机场店管理员', 0, '13992000003', 'chain_airport@example.invalid', '高成本门店菜单测试账号', 0, 1, 0, 1, 0, v_now, v_now, 920001),
    (920001004, 'chain_campus', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '校园店管理员', 0, '13992000004', 'chain_campus@example.invalid', '自主定价门店测试账号', 0, 1, 0, 1, 0, v_now, v_now, 920001),
    (920001005, 'chain_franchise', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '加盟店管理员', 0, '13992000005', 'chain_franchise@example.invalid', '清退和审批测试账号', 0, 1, 0, 1, 0, v_now, v_now, 920001),
    (920001006, 'chain_staff', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '旗舰店员工', 0, '13992000006', 'chain_staff@example.invalid', '餐桌及订单操作账号', 0, 1, 0, 1, 0, v_now, v_now, 920001),
    (920001007, 'chain_member', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '连锁测试会员', 0, '13992000007', 'chain_member@example.invalid', '点餐测试会员', 1200, 2, 500, 1, 300, v_now, v_now, 920001);

INSERT INTO `liteshop_user`.`sys_user_role` (`tenant_id`, `user_id`, `role_id`)
VALUES
    (920001, 920001001, 920001101),
    (920001, 920001002, 920001102),
    (920001, 920001003, 920001102),
    (920001, 920001004, 920001102),
    (920001, 920001005, 920001102),
    (920001, 920001006, 920001103),
    (920001, 920001007, 920001104);

INSERT INTO `liteshop_user`.`sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 920001, 920001101, p.id
FROM `liteshop_user`.`sys_permission` p
WHERE p.status = 1 AND (p.menu_scope IS NULL OR p.menu_scope <> 1)
  AND p.permission_code NOT LIKE 'platform:%';

INSERT INTO `liteshop_user`.`sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 920001, 920001102, p.id
FROM `liteshop_user`.`sys_permission` p
WHERE p.status = 1 AND (p.menu_scope IS NULL OR p.menu_scope <> 1)
  AND (p.permission_code LIKE 'restaurant:%' OR p.permission_code LIKE 'operations:%'
    OR p.permission_code LIKE 'common:%')
  AND p.permission_code NOT LIKE 'restaurant:tenant-analysis%';

INSERT INTO `liteshop_user`.`sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 920001, 920001103, p.id
FROM `liteshop_user`.`sys_permission` p
WHERE p.status = 1
  AND (p.permission_code = 'restaurant:menu'
    OR p.permission_code LIKE 'restaurant:order%'
    OR p.permission_code LIKE 'restaurant:table%'
    OR p.permission_code LIKE 'common:%'
    OR p.permission_code = 'operations:menu'
    OR p.permission_code LIKE 'operations:direct-cashier%'
    OR p.permission_code LIKE 'operations:table-cashier%');

INSERT INTO `liteshop_user`.`sys_user_store`
(`id`, `tenant_id`, `user_id`, `store_id`, `is_default`, `create_time`)
VALUES
    (920001151, 920001, 920001002, 920001201, 1, v_now),
    (920001152, 920001, 920001003, 920001202, 1, v_now),
    (920001153, 920001, 920001004, 920001203, 1, v_now),
    (920001154, 920001, 920001005, 920001204, 1, v_now),
    (920001155, 920001, 920001006, 920001201, 1, v_now);

-- 二、四种经营场景的门店
INSERT INTO `liteshop_restaurant`.`store_info`
(`id`, `store_name`, `store_code`, `address`, `phone`, `business_hours`,
 `payment_mode`, `pickup_mode`, `is_print`, `allow_pre_order`, `pre_order_limit`,
 `status`, `description`, `store_images`, `longitude`, `latitude`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001201, '星河食集·中心旗舰店', 'CHAIN_FLAGSHIP', '测试市中心区星河路1号', '13992000101', '08:00-22:00', 2, 0, 0, 1, 15, 1, '标准价格旗舰店；部分操作需要总部审批', NULL, 116.397, 39.908, v_now, v_now, 920001),
    (920001202, '星河食集·机场店', 'CHAIN_AIRPORT', '测试市机场T2航站楼出发层', '13992000102', '06:00-23:00', 2, 1, 0, 1, 20, 1, '租金和人工较高，允许较高门店售价', NULL, 116.587, 40.080, v_now, v_now, 920001),
    (920001203, '星河食集·大学城店', 'CHAIN_CAMPUS', '测试市大学城学府路88号', '13992000103', '09:00-21:30', 2, 1, 0, 1, 10, 1, '价格敏感门店，允许一定范围自主调价', NULL, 116.320, 39.990, v_now, v_now, 920001),
    (920001204, '星河食集·加盟示范店', 'CHAIN_FRANCHISE', '测试市新城区合作路18号', '13992000104', '10:00-22:00', 2, 0, 0, 1, 15, 1, '验证菜品清退、门店自建和总部审批', NULL, 116.500, 39.950, v_now, v_now, 920001);

-- 三、总部分类与门店自建分类
INSERT INTO `liteshop_restaurant`.`dish_category`
(`id`, `store_id`, `source_type`, `owner_store_id`, `deleted`, `category_name`,
 `category_desc`, `parent_id`, `level`, `sort_order`, `is_show`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001301, NULL, 0, NULL, 0, '招牌主食', '总部统一主食分类', 0, 1, 10, 1, v_now, v_now, 920001),
    (920001302, NULL, 0, NULL, 0, '小食甜品', '总部统一小食分类', 0, 1, 20, 1, v_now, v_now, 920001),
    (920001303, NULL, 0, NULL, 0, '饮品', '总部统一饮品分类', 0, 1, 30, 1, v_now, v_now, 920001),
    (920001304, 920001203, 1, 920001203, 0, '校园限定', '大学城店自建分类', 0, 1, 40, 1, v_now, v_now, 920001);

-- 四、总部统一菜品与三种门店自建菜品
INSERT INTO `liteshop_restaurant`.`dish`
(`id`, `dish_code`, `store_id`, `source_type`, `owner_store_id`, `master_status`, `version`, `deleted`,
 `category_id`, `dish_name`, `dish_desc`, `dish_pics`, `sale_price`, `cost_price`, `unit`,
 `stock`, `sales_count`, `view_count`, `is_recommend`, `is_hot`, `is_new`, `scene_type`, `sale_status`,
 `sort_order`, `tags`, `allergens`, `nutrition_info`, `cooking_time`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001401, 'HQ-BEEF-RICE', NULL, 0, NULL, 1, 1, 0, 920001301, '招牌牛肉饭', '总部统一菜品，用于验证各门店差异价格', '[]', 36.00, 12.00, '份', 999, 0, 0, 1, 1, 0, 2, 1, 10, '["招牌","推荐"]', NULL, NULL, 8, v_now, v_now, 920001),
    (920001402, 'HQ-CHICKEN-RICE', NULL, 0, NULL, 1, 1, 0, 920001301, '香煎鸡腿饭', '总部统一菜品，用于验证清退和下架', '[]', 30.00, 10.00, '份', 999, 0, 0, 1, 0, 0, 2, 1, 20, '["热销"]', NULL, NULL, 9, v_now, v_now, 920001),
    (920001403, 'HQ-ORANGE-JUICE', NULL, 0, NULL, 1, 1, 0, 920001303, '鲜榨橙汁', '总部统一饮品，用于验证售罄和定时调价', '[]', 15.00, 4.00, '杯', 999, 0, 0, 0, 1, 0, 2, 1, 30, '["鲜榨"]', NULL, NULL, 3, v_now, v_now, 920001),
    (920001404, 'HQ-CHEESE-CAKE', NULL, 0, NULL, 1, 1, 0, 920001302, '芝士蛋糕', '总部统一甜品，用于验证门店改价审批', '[]', 22.00, 7.00, '块', 999, 0, 0, 0, 0, 1, 2, 1, 40, '["甜品","新品"]', '乳制品', NULL, 2, v_now, v_now, 920001),
    (920001405, 'HQ-SEASON-SOUP', NULL, 0, NULL, 2, 1, 0, 920001302, '季节限定汤', '总部已全局停用，验证总部状态覆盖门店状态', '[]', 18.00, 5.00, '份', 999, 0, 0, 0, 0, 0, 2, 0, 50, '["季节限定"]', NULL, NULL, 6, v_now, v_now, 920001),
    (920001411, 'LOCAL-AIRPORT-COMBO', 920001202, 1, 920001202, 1, 1, 0, 920001301, '机场快捷套餐', '机场店自建菜品，仅机场店可见', '[]', 62.00, 24.00, '套', 80, 0, 0, 1, 0, 1, 2, 1, 60, '["门店限定"]', NULL, NULL, 5, v_now, v_now, 920001),
    (920001412, 'LOCAL-CAMPUS-BOWL', 920001203, 1, 920001203, 1, 1, 0, 920001304, '校园能量碗', '大学城店自建菜品和分类', '[]', 18.00, 6.00, '份', 120, 0, 0, 1, 0, 1, 2, 1, 70, '["校园限定"]', NULL, NULL, 5, v_now, v_now, 920001),
    (920001413, 'LOCAL-FRANCHISE-SNACK', 920001204, 1, 920001204, 1, 1, 0, 920001302, '加盟店拼盘', '加盟店自建菜品，仅加盟示范店可见', '[]', 45.00, 17.00, '份', 60, 0, 0, 0, 1, 0, 2, 1, 80, '["门店限定"]', NULL, NULL, 7, v_now, v_now, 920001);

-- 五、门店覆盖配置：标准价、高价、低价、下架、清退、售罄均有样例
INSERT INTO `liteshop_restaurant`.`store_dish`
(`id`, `store_id`, `dish_id`, `category_override_id`, `sale_price_override`,
 `cost_price_override`, `listing_status`, `sold_out`, `inventory_mode`, `stock`,
 `recommend_override`, `hot_override`, `new_override`, `sort_order_override`, `sales_count`,
 `inherited_version`, `config_version`, `deleted`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001501, 920001201, 920001401, NULL, NULL, NULL, 1, 0, 1, 100, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001502, 920001201, 920001402, NULL, NULL, NULL, 1, 0, 1, 80, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001503, 920001201, 920001403, NULL, NULL, NULL, 1, 1, 1, 0, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001504, 920001201, 920001404, NULL, NULL, NULL, 1, 0, 1, 60, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001505, 920001201, 920001405, NULL, NULL, NULL, 1, 0, 1, 30, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001506, 920001202, 920001401, NULL, 42.00, 16.00, 1, 0, 1, 120, 1, NULL, NULL, NULL, 0, 1, 2, 0, v_now, v_now, 920001),
    (920001507, 920001202, 920001402, NULL, 32.00, 13.00, 1, 0, 1, 100, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001508, 920001202, 920001403, NULL, 18.00, 5.00, 1, 0, 1, 90, NULL, 1, NULL, NULL, 0, 1, 2, 0, v_now, v_now, 920001),
    (920001509, 920001202, 920001404, NULL, NULL, NULL, 1, 0, 1, 50, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001510, 920001202, 920001405, NULL, NULL, NULL, 1, 0, 1, 20, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001511, 920001203, 920001401, NULL, 32.00, 10.00, 1, 0, 1, 150, NULL, NULL, NULL, NULL, 0, 1, 2, 0, v_now, v_now, 920001),
    (920001512, 920001203, 920001402, NULL, 26.00, 8.50, 1, 0, 1, 130, NULL, NULL, NULL, NULL, 0, 1, 2, 0, v_now, v_now, 920001),
    (920001513, 920001203, 920001403, NULL, NULL, NULL, 1, 0, 1, 100, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001514, 920001203, 920001404, NULL, NULL, NULL, 0, 0, 1, 40, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001515, 920001203, 920001405, NULL, NULL, NULL, 1, 0, 1, 20, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001516, 920001204, 920001401, NULL, NULL, NULL, 2, 0, 1, 0, NULL, NULL, NULL, NULL, 0, 1, 2, 0, v_now, v_now, 920001),
    (920001517, 920001204, 920001402, NULL, NULL, NULL, 1, 0, 1, 80, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001518, 920001204, 920001403, NULL, 16.00, NULL, 1, 0, 1, 70, NULL, NULL, NULL, NULL, 0, 1, 2, 0, v_now, v_now, 920001),
    (920001519, 920001204, 920001404, NULL, NULL, NULL, 1, 1, 1, 0, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001520, 920001204, 920001405, NULL, NULL, NULL, 1, 0, 1, 20, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001521, 920001202, 920001411, NULL, NULL, NULL, 1, 0, 1, 80, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001522, 920001203, 920001412, NULL, NULL, NULL, 1, 0, 1, 120, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001),
    (920001523, 920001204, 920001413, NULL, NULL, NULL, 1, 0, 1, 60, NULL, NULL, NULL, NULL, 0, 1, 1, 0, v_now, v_now, 920001);

-- 六、每家门店采用不同菜单权限策略
INSERT INTO `liteshop_restaurant`.`store_menu_policy`
(`id`, `store_id`, `allow_local_dish`, `allow_local_category`, `allow_category_override`,
 `allow_price_override`, `allow_store_off_shelf`,
 `allow_store_exit`, `min_price_rate`, `max_price_rate`, `require_approval`,
 `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001601, 920001201, 0, 0, 0, 0, 1, 0, 0.9000, 1.2000, 1, v_now, v_now, 920001),
    (920001602, 920001202, 1, 1, 1, 1, 1, 1, 0.9000, 1.5000, 1, v_now, v_now, 920001),
    (920001603, 920001203, 1, 0, 1, 1, 1, 1, 0.8000, 1.1000, 0, v_now, v_now, 920001),
    (920001604, 920001204, 1, 1, 0, 1, 1, 1, 0.8500, 1.3000, 1, v_now, v_now, 920001);

-- 七、总部批量发布分组
INSERT INTO `liteshop_restaurant`.`store_group`
(`id`, `group_code`, `group_name`, `description`, `status`, `deleted`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001701, 'ALL_STORES', '全部营业门店', '总部全量菜单发布范围', 1, 0, v_now, v_now, 920001),
    (920001702, 'URBAN_VALUE', '城市与校园店', '标准或优惠定价门店', 1, 0, v_now, v_now, 920001),
    (920001703, 'HIGH_COST', '高成本与加盟店', '允许较高售价的门店', 1, 0, v_now, v_now, 920001);

INSERT INTO `liteshop_restaurant`.`store_group_member`
(`id`, `group_id`, `store_id`, `created_at`, `tenant_id`)
VALUES
    (920001711, 920001701, 920001201, v_now, 920001),
    (920001712, 920001701, 920001202, v_now, 920001),
    (920001713, 920001701, 920001203, v_now, 920001),
    (920001714, 920001701, 920001204, v_now, 920001),
    (920001715, 920001702, 920001201, v_now, 920001),
    (920001716, 920001702, 920001203, v_now, 920001),
    (920001717, 920001703, 920001202, v_now, 920001),
    (920001718, 920001703, 920001204, v_now, 920001);

-- 八、审批与审计状态样例；待生效记录会在未来 1 天由定时任务执行。
INSERT INTO `liteshop_restaurant`.`menu_change_record`
(`id`, `request_no`, `store_id`, `dish_id`, `change_type`, `before_snapshot`, `after_snapshot`,
 `status`, `change_reason`, `effective_at`, `requested_by`, `reviewed_by`, `review_remark`,
 `reviewed_at`, `applied_at`, `failure_reason`, `version`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001801, 'MC-CHAIN-APPLIED-001', 920001202, 920001401, 0,
     JSON_OBJECT('storeId',920001202,'dishId',920001401,'salePriceOverride',NULL,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',120,'expectedConfigVersion',1),
     JSON_OBJECT('storeId',920001202,'dishId',920001401,'salePriceOverride',42.00,'costPriceOverride',16.00,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',120,'expectedConfigVersion',1),
     5, '机场店经营成本上涨，审批后调整售价', DATE_SUB(v_now, INTERVAL 2 DAY), 920001003, 920001001, '符合高成本门店定价范围', DATE_SUB(v_now, INTERVAL 3 DAY), DATE_SUB(v_now, INTERVAL 2 DAY), NULL, 3, DATE_SUB(v_now, INTERVAL 3 DAY), DATE_SUB(v_now, INTERVAL 2 DAY), 920001),
    (920001802, 'MC-CHAIN-PENDING-001', 920001202, 920001404, 0,
     JSON_OBJECT('storeId',920001202,'dishId',920001404,'salePriceOverride',NULL,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',50,'expectedConfigVersion',1),
     JSON_OBJECT('storeId',920001202,'dishId',920001404,'salePriceOverride',25.00,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',50,'expectedConfigVersion',1),
     0, '机场店申请提高甜品售价', NULL, 920001003, NULL, NULL, NULL, NULL, NULL, 1, DATE_SUB(v_now, INTERVAL 30 MINUTE), DATE_SUB(v_now, INTERVAL 30 MINUTE), 920001),
    (920001803, 'MC-CHAIN-SCHEDULED-001', 920001203, 920001403, 0,
     JSON_OBJECT('storeId',920001203,'dishId',920001403,'salePriceOverride',NULL,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',100,'expectedConfigVersion',1),
     JSON_OBJECT('storeId',920001203,'dishId',920001403,'salePriceOverride',12.00,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',100,'expectedConfigVersion',1),
     4, '校园开学季限时价格', DATE_ADD(v_now, INTERVAL 1 DAY), 920001001, NULL, NULL, NULL, NULL, NULL, 1, DATE_SUB(v_now, INTERVAL 1 HOUR), DATE_SUB(v_now, INTERVAL 1 HOUR), 920001),
    (920001804, 'MC-CHAIN-REJECTED-001', 920001201, 920001401, 0,
     JSON_OBJECT('storeId',920001201,'dishId',920001401,'salePriceOverride',NULL,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',100,'expectedConfigVersion',1),
     JSON_OBJECT('storeId',920001201,'dishId',920001401,'salePriceOverride',20.00,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',100,'expectedConfigVersion',1),
     2, '门店申请大幅降价', NULL, 920001002, 920001001, '低于总部允许的最低价格比例', DATE_SUB(v_now, INTERVAL 1 DAY), NULL, NULL, 2, DATE_SUB(v_now, INTERVAL 2 DAY), DATE_SUB(v_now, INTERVAL 1 DAY), 920001),
    (920001805, 'MC-CHAIN-CANCELLED-001', 920001204, 920001402, 0,
     JSON_OBJECT('storeId',920001204,'dishId',920001402,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',80,'expectedConfigVersion',1),
     JSON_OBJECT('storeId',920001204,'dishId',920001402,'listingStatus',2,'soldOut',0,'inventoryMode',1,'stock',80,'expectedConfigVersion',1),
     3, '加盟店临时申请清退，随后恢复经营', NULL, 920001005, NULL, NULL, NULL, NULL, NULL, 2, DATE_SUB(v_now, INTERVAL 2 DAY), DATE_SUB(v_now, INTERVAL 1 DAY), 920001),
    (920001806, 'MC-CHAIN-FAILED-001', 920001202, 920001403, 0,
     JSON_OBJECT('storeId',920001202,'dishId',920001403,'salePriceOverride',NULL,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',90,'expectedConfigVersion',1),
     JSON_OBJECT('storeId',920001202,'dishId',920001403,'salePriceOverride',14.00,'listingStatus',1,'soldOut',0,'inventoryMode',1,'stock',90,'expectedConfigVersion',1),
     6, '历史调价申请，用于验证版本冲突失败状态', DATE_SUB(v_now, INTERVAL 1 HOUR), 920001003, 920001001, '审批通过', DATE_SUB(v_now, INTERVAL 2 HOUR), NULL, '门店菜品配置已发生变化，请刷新后重新提交', 3, DATE_SUB(v_now, INTERVAL 3 HOUR), DATE_SUB(v_now, INTERVAL 1 HOUR), 920001);

-- 九、定价中心样例：第二件八折 + 1级会员菜品价。
INSERT INTO `liteshop_restaurant`.`pricing_campaign`
(`id`, `campaign_code`, `campaign_name`, `campaign_type`, `start_time`, `end_time`,
 `priority`, `exclusive_group`, `stack_mode`, `status`, `version`, `deleted`,
 `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001850, 'SECOND_ITEM_80', '招牌牛肉饭第二件八折', 1, NULL, NULL,
     100, 'QUANTITY_PROMOTION', 1, 1, 1, 0, v_now, v_now, 920001);

INSERT INTO `liteshop_restaurant`.`pricing_rule`
(`id`, `campaign_id`, `condition_type`, `minimum_quantity`, `minimum_amount`, `nth_quantity`,
 `grouping_type`, `action_type`, `action_value`, `repeat_mode`, `tenant_id`)
VALUES
    (920001851, 920001850, 1, 2, NULL, 2, 0, 1, 0.8000, 0, 920001);

INSERT INTO `liteshop_restaurant`.`pricing_campaign_scope`
(`id`, `campaign_id`, `store_id`, `target_type`, `target_id`, `tenant_id`)
VALUES
    (920001852, 920001850, NULL, 1, 920001401, 920001);

INSERT INTO `liteshop_restaurant`.`pricing_campaign_audience`
(`id`, `campaign_id`, `audience_type`, `audience_value`, `tenant_id`)
VALUES
    (920001853, 920001850, 0, NULL, 920001);

INSERT INTO `liteshop_restaurant`.`member_dish_price`
(`id`, `store_id`, `dish_id`, `member_level_code`, `member_price`, `start_time`, `end_time`,
 `priority`, `status`, `deleted`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001860, NULL, 920001401, 2, 32.00, NULL, NULL, 100, 1, 0, v_now, v_now, 920001);

-- 十、每店两张餐桌，方便登录后验证门店数据隔离。
INSERT INTO `liteshop_restaurant`.`table_info`
(`id`, `store_id`, `table_number`, `table_name`, `seats`, `location`, `status`, `created_at`, `updated_at`, `tenant_id`)
VALUES
    (920001901, 920001201, 'F01', '旗舰店大厅1号桌', 4, '大厅', 0, v_now, v_now, 920001),
    (920001902, 920001201, 'F02', '旗舰店大厅2号桌', 6, '大厅', 0, v_now, v_now, 920001),
    (920001903, 920001202, 'A01', '机场店取餐桌1', 2, '候机区', 0, v_now, v_now, 920001),
    (920001904, 920001202, 'A02', '机场店取餐桌2', 2, '候机区', 3, v_now, v_now, 920001),
    (920001905, 920001203, 'C01', '校园店1号桌', 4, '一层', 0, v_now, v_now, 920001),
    (920001906, 920001203, 'C02', '校园店2号桌', 4, '二层', 0, v_now, v_now, 920001),
    (920001907, 920001204, 'J01', '加盟店1号桌', 4, '大厅', 0, v_now, v_now, 920001),
    (920001908, 920001204, 'J02', '加盟店2号桌', 8, '包间', 0, v_now, v_now, 920001);

INSERT INTO `liteshop_config`.`sys_config`
(`id`, `config_key`, `config_value`, `config_type`, `config_group`, `config_name`,
 `description`, `editable`, `tenant_id`)
VALUES
    (920001951, 'app.name', '星河食集连锁测试', 2, 2, '应用名称', '连锁菜单专项测试租户', 1, 920001),
    (920001952, 'contact.phone', '13992000000', 2, 4, '客服电话', '测试客服电话', 1, 920001),
    (920001953, 'theme.primaryColor', '#2563EB', 4, 3, '主题色', '测试主题色', 1, 920001),
    (920001954, 'order.timeoutMinutes', '15', 5, 5, '订单超时分钟数', '测试订单超时配置', 1, 920001);

-- 十一、租户独立会员体系与有效专业版订阅（20家门店额度）。
INSERT INTO liteshop_user.user_level_scheme
(id,tenant_id,name,description,is_active,is_default,status)
VALUES (920002001,920001,'星河餐饮会员体系','模拟会员等级与会员价联调',1,0,1);
INSERT INTO liteshop_user.user_level
(id,tenant_id,scheme_id,level_code,level_name,required_exp,description,benefits,status,sort)
VALUES
    (920002011,920001,920002001,1,'普通会员',0,'默认会员','["基础会员权益"]',1,1),
    (920002012,920001,920002001,2,'银卡会员',1000,'对应chain_member','["牛肉饭会员价32元"]',1,2),
    (920002013,920001,920002001,3,'金卡会员',5000,'成长等级','["专属优惠券"]',1,3),
    (920002014,920001,920002001,4,'黑金会员',20000,'高级会员','["专属优惠券","优先客服"]',1,4);
INSERT INTO liteshop_user.saas_tenant_subscription
(id,tenant_id,plan_version_id,billing_cycle,status,start_at,current_period_start,current_period_end)
SELECT 920002021,920001,v.id,'YEARLY','ACTIVE',DATE_SUB(v_now,INTERVAL 30 DAY),
       DATE_SUB(v_now,INTERVAL 30 DAY),DATE_ADD(v_now,INTERVAL 335 DAY)
FROM liteshop_user.saas_plan_version v JOIN liteshop_user.saas_plan p ON p.id=v.plan_id
WHERE p.plan_code='pro' AND v.status='PUBLISHED'
  AND EXISTS (
    SELECT 1 FROM liteshop_user.saas_plan_entitlement e
      JOIN liteshop_user.saas_feature f ON f.id=e.feature_id
    WHERE e.plan_version_id=v.id AND e.enabled=1 AND f.feature_code='ai_manager'
  )
  AND EXISTS (
    SELECT 1 FROM liteshop_user.saas_plan_entitlement e
      JOIN liteshop_user.saas_feature f ON f.id=e.feature_id
    WHERE e.plan_version_id=v.id AND e.enabled=1 AND e.quota_value>0
      AND f.feature_code='ai_manager_calls'
  )
ORDER BY v.version_no DESC
LIMIT 1;

-- 十二、直营分组直接促销、全门店订单满减及未来/过期/停用活动。
INSERT INTO liteshop_restaurant.pricing_campaign
(id,campaign_code,campaign_name,campaign_type,start_time,end_time,priority,
 exclusive_group,stack_mode,status,version,deleted,created_by,updated_by,tenant_id)
VALUES
    (920002101,'URBAN_BEEF','城市校园牛肉饭特价',0,DATE_SUB(v_now,INTERVAL 30 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),80,'DIRECT_PRICE',0,1,1,0,920001001,920001001,920001),
    (920002102,'ORDER_MINUS_10','全门店满100减10',2,DATE_SUB(v_now,INTERVAL 30 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),50,'ORDER_DISCOUNT',0,1,1,0,920001001,920001001,920001),
    (920002103,'FUTURE_JUICE','明日橙汁9折',0,DATE_ADD(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 7 DAY),60,'DIRECT_PRICE',0,1,1,0,920001001,920001001,920001),
    (920002104,'EXPIRED_CAKE','已结束甜品促销',0,DATE_SUB(v_now,INTERVAL 60 DAY),DATE_SUB(v_now,INTERVAL 31 DAY),60,'DIRECT_PRICE',0,1,1,0,920001001,920001001,920001),
    (920002105,'DISABLED_SOUP','已停用汤品活动',0,NULL,NULL,60,'DIRECT_PRICE',0,0,1,0,920001001,920001001,920001);
INSERT INTO liteshop_restaurant.pricing_rule
(id,campaign_id,condition_type,minimum_amount,action_type,action_value,repeat_mode,tenant_id)
VALUES
    (920002111,920002101,0,NULL,0,28.00,0,920001),
    (920002112,920002102,2,100.00,2,10.00,0,920001),
    (920002113,920002103,0,NULL,1,0.9000,0,920001),
    (920002114,920002104,0,NULL,0,18.00,0,920001),
    (920002115,920002105,0,NULL,2,3.00,0,920001);
INSERT INTO liteshop_restaurant.pricing_campaign_scope
(id,campaign_id,store_id,store_group_id,target_type,target_id,tenant_id)
VALUES
    (920002121,920002101,NULL,920001702,1,920001401,920001),
    (920002122,920002102,NULL,NULL,0,NULL,920001),
    (920002123,920002103,920001202,NULL,2,920001303,920001),
    (920002124,920002104,NULL,NULL,1,920001404,920001),
    (920002125,920002105,NULL,NULL,1,920001405,920001);
INSERT INTO liteshop_restaurant.pricing_campaign_audience
(id,campaign_id,audience_type,audience_value,tenant_id)
VALUES
    (920002131,920002101,0,NULL,920001), (920002132,920002102,0,NULL,920001),
    (920002133,920002103,2,2,920001), (920002134,920002104,0,NULL,920001),
    (920002135,920002105,1,NULL,920001);

-- 十三、最近30天，每店每天6单；金额以订单项计算，含成本和定价快照。
-- 第4单模拟部分退菜；第5单取消；第6单含第2件优惠和赠品。
WHILE v_day < 30 DO
    SET v_store = 1;
    WHILE v_store <= 4 DO
      SET v_slot = 1;
      WHILE v_slot <= 6 DO
        SET v_order_id = 920010000 + v_day * 100 + v_store * 10 + v_slot;
        SET v_time = IF(v_day=0, DATE_SUB(v_now,INTERVAL LEAST(40 + v_slot * 5,
          TIMESTAMPDIFF(MINUTE,DATE(v_now),v_now)) MINUTE),
          TIMESTAMP(DATE_SUB(DATE(v_now),INTERVAL v_day DAY),
            MAKETIME(ELT(v_slot,10,12,16,18,20,21),v_store * 3,0)));
        SET v_payment = CASE v_slot WHEN 4 THEN 2 WHEN 5 THEN 5 ELSE 1 END;
        SET v_status = IF(v_slot=5,6,5);
        -- 加盟店牛肉饭已清退，历史订单发生时仍有销售；快照不受当前状态影响。
        SET v_price = CASE v_store WHEN 2 THEN 42.00 WHEN 3 THEN 32.00 ELSE 36.00 END;
        SET v_cost = IF(v_store=2,16.00,12.00);
        SET v_quantity = IF(v_slot=1,3+MOD(v_day+v_store,3),2);
        SET v_discount = CASE
          WHEN v_slot=2 THEN GREATEST(v_price-32.00,0)*v_quantity
          WHEN v_slot=3 AND v_store IN (1,3) THEN (v_price-28.00)*v_quantity
          WHEN v_slot=6 THEN v_price*0.20 ELSE 0 END;
        SET v_campaign = CASE WHEN v_slot=3 AND v_store IN (1,3) THEN 920002101
          WHEN v_slot=6 THEN 920001850 ELSE NULL END;
INSERT INTO liteshop_restaurant.restaurant_order
(id,order_no,store_id,user_id,order_type,dining_count,total_amount,discount_amount,
 actual_amount,payment_mode,payment_status,payment_type,payment_time,payment_no,
 pickup_code,status,note,cancel_reason,cancel_time,created_at,updated_at,dining_end_time,tenant_id)
VALUES
    (v_order_id,CONCAT('SIM-',v_order_id),920001200+v_store,
     IF(v_slot=2,920001007,NULL),MOD(v_slot,3),2,
     v_price*v_quantity+IF(v_store=2,18.00,15.00),
     v_discount+IF(v_slot=6,IF(v_store=2,18.00,15.00),0),
     IF(v_slot=5,0,v_price*v_quantity-v_discount+IF(v_slot=6,0,IF(v_store=2,18.00,15.00))),
     2,v_payment,IF(v_slot=5,NULL,1+MOD(v_slot,4)),
     IF(v_slot=5,NULL,LEAST(DATE_ADD(v_time,INTERVAL 5 MINUTE),v_now)),
     IF(v_slot=5,NULL,CONCAT('SIM-PAY-',v_order_id)),CONCAT('S',v_store,v_slot),v_status,
     ELT(v_slot,'常规订单','会员价订单','直接促销订单','已退1份牛肉饭','顾客取消','第二件优惠及赠品'),
     IF(v_slot=5,'顾客行程变更',NULL),IF(v_slot=5,LEAST(DATE_ADD(v_time,INTERVAL 3 MINUTE),v_now),NULL),
     v_time,LEAST(DATE_ADD(v_time,INTERVAL 30 MINUTE),v_now),
     IF(v_slot=5,NULL,LEAST(DATE_ADD(v_time,INTERVAL 30 MINUTE),v_now)),920001);
INSERT INTO liteshop_restaurant.restaurant_order_item
(id,order_id,dish_id,category_id,unit_list_price,unit_selling_price,unit_cost_snapshot,
 quantity,discount_amount,line_payable_amount,line_cost_amount,is_gift,
 batch_no,add_time,created_at,updated_at,tenant_id)
VALUES
    (v_order_id*10+1,v_order_id,920001401,920001301,v_price,
     ROUND((v_price*v_quantity-v_discount)/v_quantity,2),v_cost,v_quantity,v_discount,
     v_price*v_quantity-v_discount,v_cost*v_quantity,0,1,v_time,v_time,v_time,920001),
    (v_order_id*10+2,v_order_id,920001403,920001303,IF(v_store=2,18.00,15.00),
     IF(v_slot=6,0,IF(v_store=2,18.00,15.00)),IF(v_store=2,5.00,4.00),1,
     IF(v_slot=6,IF(v_store=2,18.00,15.00),0),
     IF(v_slot=6,0,IF(v_store=2,18.00,15.00)),IF(v_store=2,5.00,4.00),
     IF(v_slot=6,1,0),IF(v_slot=6,2,1),
     IF(v_slot=6,LEAST(DATE_ADD(v_time,INTERVAL 2 MINUTE),v_now),v_time),v_time,v_time,920001);
IF v_discount > 0 THEN
          INSERT INTO liteshop_restaurant.order_price_adjustment
            (id,order_id,order_item_id,campaign_id,adjustment_type,before_amount,
             adjustment_amount,after_amount,rule_snapshot,created_at,tenant_id)
          VALUES
            (v_order_id*10+3,v_order_id,v_order_id*10+1,v_campaign,
             CASE v_slot WHEN 2 THEN 0 WHEN 3 THEN 1 ELSE 2 END,v_price*v_quantity,
             v_discount,v_price*v_quantity-v_discount,
             JSON_OBJECT('memberLevelCode',IF(v_slot=2,2,NULL),'campaignId',v_campaign,
               'unitListPrice',v_price,'quantity',v_quantity,'discountAmount',v_discount),v_time,920001);
END IF;
        IF v_slot=4 THEN
          INSERT INTO liteshop_restaurant.order_dish_change_log
            (id,event_key,order_id,order_item_id,store_id,dish_id,category_id,
             change_type,quantity,amount,reason,occurred_at,tenant_id)
          VALUES
            (v_order_id*10+4,CONCAT('SIM-REFUND-',v_order_id),v_order_id,v_order_id*10+1,
             920001200+v_store,920001401,920001301,2,1,v_price,'顾客少用1份',
             LEAST(DATE_ADD(v_time,INTERVAL 15 MINUTE),v_now),920001);
END IF;
        IF v_slot=6 THEN
          INSERT INTO liteshop_restaurant.order_price_adjustment
            (id,order_id,order_item_id,adjustment_type,before_amount,adjustment_amount,
             after_amount,rule_snapshot,created_at,tenant_id)
          VALUES
            (v_order_id*10+5,v_order_id,v_order_id*10+2,4,IF(v_store=2,18.00,15.00),
             IF(v_store=2,18.00,15.00),0,JSON_OBJECT('reason','模拟人工赠送饮品'),v_time,920001);
INSERT INTO liteshop_restaurant.order_dish_change_log
(id,event_key,order_id,order_item_id,store_id,dish_id,category_id,
 change_type,quantity,amount,reason,occurred_at,tenant_id)
VALUES
    (v_order_id*10+6,CONCAT('SIM-ADD-',v_order_id),v_order_id,v_order_id*10+2,
     920001200+v_store,920001403,920001303,1,1,0,'第二批加赠饮品',
     LEAST(DATE_ADD(v_time,INTERVAL 2 MINUTE),v_now),920001);
END IF;
        SET v_slot = v_slot+1;
END WHILE;
      SET v_store = v_store+1;
END WHILE;
    SET v_day = v_day+1;
END WHILE;

  -- 十四、进行中的桌单和直接收银待支付单，创建时间保持在超时窗口内。
INSERT INTO liteshop_restaurant.restaurant_order
(id,order_no,store_id,table_id,session_no,order_type,dining_count,total_amount,
 discount_amount,actual_amount,payment_mode,payment_status,status,created_at,updated_at,tenant_id)
VALUES
    (920020001,'SIM-ACTIVE-TABLE',920001201,920001901,'SIM-SESSION-ACTIVE',0,2,72,0,72,2,0,3,DATE_SUB(v_now,INTERVAL 2 MINUTE),v_now,920001),
    (920020002,'SIM-ACTIVE-DIRECT',920001202,NULL,NULL,2,1,42,0,42,2,0,1,DATE_SUB(v_now,INTERVAL 1 MINUTE),v_now,920001);
INSERT INTO liteshop_restaurant.restaurant_order_item
(id,order_id,dish_id,category_id,unit_list_price,unit_selling_price,unit_cost_snapshot,
 quantity,line_payable_amount,line_cost_amount,created_at,updated_at,add_time,tenant_id)
VALUES
    (9200200011,920020001,920001401,920001301,36,36,12,2,72,24,v_now,v_now,v_now,920001),
    (9200200021,920020002,920001401,920001301,42,42,16,1,42,16,v_now,v_now,v_now,920001);
UPDATE liteshop_restaurant.table_info SET status=1 WHERE tenant_id=920001 AND id=920001901;
INSERT INTO liteshop_restaurant.table_session
(id,session_no,store_id,table_id,current_order_id,current_user_count,people_count,
 start_time,total_orders,total_amount,paid_amount,status,tenant_id)
VALUES
    (920002201,'SIM-SESSION-ACTIVE',920001201,920001901,920020001,0,2,
     DATE_SUB(v_now,INTERVAL 2 MINUTE),1,72,0,1,920001);
INSERT INTO liteshop_restaurant.table_reservation
(id,store_id,table_id,reservation_phone,people_count,expected_arrival_time,expected_end_time,
 reservation_remark,status,session_no,operator_id,cancel_reason,arrived_at,completed_at,cancelled_at,tenant_id)
VALUES
    (920002211,920001201,920001902,'13992001001',4,DATE_ADD(v_now,INTERVAL 2 HOUR),DATE_ADD(v_now,INTERVAL 4 HOUR),'待到店预约',0,NULL,920001002,NULL,NULL,NULL,NULL,920001),
    (920002212,920001201,920001901,'13992001002',2,DATE_SUB(v_now,INTERVAL 5 MINUTE),DATE_ADD(v_now,INTERVAL 2 HOUR),'已到店开台',1,'SIM-SESSION-ACTIVE',920001002,NULL,DATE_SUB(v_now,INTERVAL 2 MINUTE),NULL,NULL,920001),
    (920002213,920001203,920001905,'13992001003',3,DATE_SUB(v_now,INTERVAL 2 DAY),DATE_SUB(v_now,INTERVAL 46 HOUR),'已完成预约',2,NULL,920001004,NULL,DATE_SUB(v_now,INTERVAL 2 DAY),DATE_SUB(v_now,INTERVAL 46 HOUR),NULL,920001),
    (920002214,920001204,920001908,'13992001004',6,DATE_ADD(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 26 HOUR),'已取消预约',3,NULL,920001005,'顾客取消',NULL,NULL,v_now,920001),
    (920002215,920001202,920001903,'13992001005',2,DATE_SUB(v_now,INTERVAL 1 DAY),DATE_SUB(v_now,INTERVAL 22 HOUR),'爽约记录',4,NULL,920001003,NULL,NULL,NULL,NULL,920001);
INSERT INTO liteshop_restaurant.print_template
(id,template_name,store_id,paper_width,is_default,enabled,show_qrcode,remark,tenant_id)
VALUES
    (920002221,'星河58mm收银模板',NULL,0,1,1,0,'预览测试，无真实打印设备',920001),
    (920002222,'机场80mm明细模板',920001202,1,1,1,0,'预览订单与规格明细',920001);

-- 十五、满减/折扣/菜品券；领取记录含未生效、可用和已过期状态。
INSERT INTO liteshop_coupon.coupon
(id,name,description,type,value,min_amount,total_count,remain_count,start_time,end_time,status,stackable,tenant_id)
VALUES
    (920002301,'新客满50减5','模拟收银优惠券',0,5,50,100,98,DATE_SUB(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),1,0,920001),
    (920002302,'会员9折券','整单折扣联调',1,0.9,0,100,99,DATE_SUB(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),1,0,920001),
    (920002303,'牛肉饭免单券','关联总部牛肉饭',2,36,0,100,99,DATE_SUB(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),1,0,920001),
    (920002304,'已结束满减券','过期列表测试',0,3,30,100,99,DATE_SUB(v_now,INTERVAL 60 DAY),DATE_SUB(v_now,INTERVAL 1 DAY),0,0,920001);
INSERT INTO liteshop_coupon.coupon_dish_item (id,coupon_id,dish_id,tenant_id)
VALUES (920002311,920002303,920001401,920001);
INSERT INTO liteshop_coupon.user_coupon
(id,user_id,coupon_id,code,status,obtain_time,start_time,expire_time,tenant_id)
VALUES
    (920002321,920001007,920002301,'SIM-COUPON-01',1,v_now,DATE_SUB(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),920001),
    (920002322,920001007,920002302,'SIM-COUPON-02',1,v_now,DATE_SUB(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),920001),
    (920002323,920001007,920002303,'SIM-COUPON-03',1,v_now,DATE_SUB(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),920001),
    (920002324,920001007,920002301,'SIM-COUPON-04',0,v_now,DATE_ADD(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),920001),
    (920002325,920001007,920002304,'SIM-COUPON-05',3,DATE_SUB(v_now,INTERVAL 30 DAY),DATE_SUB(v_now,INTERVAL 60 DAY),DATE_SUB(v_now,INTERVAL 1 DAY),920001);
INSERT INTO liteshop_user.user_points_record
(id,user_id,username,operation_type,source,points,before_points,after_points,remark,tenant_id)
VALUES (920002331,920001007,'chain_member',1,4,500,0,500,'模拟积分初始发放',920001);
INSERT INTO liteshop_user.user_surplus_record
(id,user_id,amount,new_balance,type,old_balance,operate_user_id,tenant_id)
VALUES (920002332,920001007,300,300,1,0,920001001,920001);

-- 十六、会员抽奖活动与门店投放公告，不包含虚构文件地址。
INSERT INTO liteshop_lottery.lottery_activity
(id,name,status,description,start_time,end_time,tenant_id)
VALUES (920002401,'星河会员幸运抽奖',1,'模拟优惠券与积分抽奖',DATE_SUB(v_now,INTERVAL 1 DAY),DATE_ADD(v_now,INTERVAL 30 DAY),920001);
INSERT INTO liteshop_lottery.lottery_prize
(id,name,probability,type,coupon_id,points,enabled,tenant_id)
VALUES
    (920002411,'新客满减券',20,0,920002301,NULL,1,920001),
    (920002412,'20积分',30,1,NULL,20,1,920001),
    (920002413,'谢谢参与',50,3,NULL,NULL,1,920001);
INSERT INTO liteshop_lottery.activity_prize
(activity_id,prize_id,total_stock,surplus_stock,tenant_id)
VALUES (920002401,920002411,50,50,920001),(920002401,920002412,100,100,920001),
       (920002401,920002413,1000,1000,920001);
INSERT INTO liteshop_lottery.user_activity_chance (id,user_id,activity_id,chances,tenant_id)
VALUES (920002421,920001007,920002401,3,920001);
INSERT INTO liteshop_message.sys_announcement
(id,tenant_id,title,content,level,status,publisher_id,publisher_name,publish_time,expire_time)
VALUES
    (920002501,920001,'星河门店模拟数据已更新','<p>请验证菜单、收银、定价和经营分析功能。</p>','NORMAL','PUBLISHED',920001001,'星河总部管理员',DATE_SUB(v_now,INTERVAL 1 HOUR),DATE_ADD(v_now,INTERVAL 30 DAY)),
    (920002502,920001,'机场店服务提示','<p>机场店请核对门店覆盖售价。</p>','IMPORTANT','PUBLISHED',920001001,'星河总部管理员',DATE_SUB(v_now,INTERVAL 1 HOUR),NULL),
    (920002503,920001,'待发布活动通知','<p>此公告为草稿样例。</p>','NORMAL','DRAFT',920001001,'星河总部管理员',NULL,NULL),
    (920002504,920001,'已撤回公告','<p>用于验证撤回状态筛选。</p>','NORMAL','WITHDRAWN',920001001,'星河总部管理员',DATE_SUB(v_now,INTERVAL 2 DAY),NULL);
INSERT INTO liteshop_message.sys_announcement_target (id,announcement_id,target_type,target_id)
VALUES (920002511,920002502,'STORE',920001202);
INSERT INTO liteshop_message.sys_announcement_read (announcement_id,user_id,tenant_id)
VALUES (920002501,920001002,920001);

-- 十七、按正式聚合口径生成日汇总，导入后即可查看总部分析和AI店长数据。
INSERT INTO liteshop_restaurant.analysis_store_daily
(tenant_id,biz_date,store_id,gross_sales_amount,net_sales_amount,discount_amount,
 valid_order_count,sales_quantity,updated_at)
SELECT ro.tenant_id,DATE(ro.payment_time),ro.store_id,SUM(ro.total_amount),SUM(ro.actual_amount),
    SUM(ro.discount_amount),COUNT(*),SUM(items.sales_quantity),v_now
FROM liteshop_restaurant.restaurant_order ro
    JOIN (SELECT tenant_id,order_id,SUM(IF(is_gift=0,quantity,0)) AS sales_quantity
    FROM liteshop_restaurant.restaurant_order_item WHERE tenant_id=920001 GROUP BY tenant_id,order_id) items
ON items.tenant_id=ro.tenant_id AND items.order_id=ro.id
WHERE ro.tenant_id=920001 AND ro.payment_status IN (1,2) AND ro.status<>6 AND ro.payment_time IS NOT NULL
GROUP BY ro.tenant_id,DATE(ro.payment_time),ro.store_id;
INSERT INTO liteshop_restaurant.analysis_store_dish_daily
(tenant_id,biz_date,store_id,dish_id,category_id,sales_quantity,gross_sales_amount,net_sales_amount,
 cost_amount,gross_profit_amount,order_count,gift_quantity,unknown_cost_quantity,updated_at)
SELECT ro.tenant_id,DATE(ro.payment_time),ro.store_id,roi.dish_id,COALESCE(roi.category_id,0),
    SUM(IF(roi.is_gift=0,roi.quantity,0)),SUM(IF(roi.is_gift=0,roi.unit_list_price*roi.quantity,0)),
    SUM(IF(roi.is_gift=0,roi.line_payable_amount,0)),SUM(roi.line_cost_amount),
    IF(SUM(IF(roi.unit_cost_snapshot IS NULL,roi.quantity,0))=0,SUM(roi.line_payable_amount-roi.line_cost_amount),NULL),
    COUNT(DISTINCT IF(roi.is_gift=0,ro.id,NULL)),SUM(IF(roi.is_gift=1,roi.quantity,0)),
    SUM(IF(roi.unit_cost_snapshot IS NULL,roi.quantity,0)),v_now
FROM liteshop_restaurant.restaurant_order ro
    JOIN liteshop_restaurant.restaurant_order_item roi ON roi.tenant_id=ro.tenant_id AND roi.order_id=ro.id
WHERE ro.tenant_id=920001 AND ro.payment_status IN (1,2) AND ro.status<>6 AND ro.payment_time IS NOT NULL
GROUP BY ro.tenant_id,DATE(ro.payment_time),ro.store_id,roi.dish_id,COALESCE(roi.category_id,0);

-- 十八、事务内校验关联、数量和金额；任何不一致都会回滚本次清理与导入。
IF (SELECT COUNT(*) FROM `liteshop_user`.`sys_user` WHERE tenant_id = 920001) <> 7
    OR (SELECT COUNT(*) FROM `liteshop_user`.`sys_role_permission` WHERE tenant_id = 920001 AND role_id = 920001101) = 0
    OR (SELECT COUNT(*) FROM `liteshop_user`.`sys_role_permission` WHERE tenant_id = 920001 AND role_id = 920001102) = 0
    OR (SELECT COUNT(*) FROM `liteshop_restaurant`.`store_info` WHERE tenant_id = 920001) <> 4
    OR (SELECT COUNT(*) FROM `liteshop_restaurant`.`dish` WHERE tenant_id = 920001) <> 8
    OR (SELECT COUNT(*) FROM `liteshop_restaurant`.`store_dish` WHERE tenant_id = 920001) <> 23
    OR (SELECT COUNT(*) FROM `liteshop_restaurant`.`store_menu_policy` WHERE tenant_id = 920001) <> 4
    OR (SELECT COUNT(*) FROM `liteshop_restaurant`.`store_group` WHERE tenant_id = 920001) <> 3
    OR (SELECT COUNT(*) FROM `liteshop_restaurant`.`menu_change_record` WHERE tenant_id = 920001) <> 6
    OR (SELECT COUNT(*) FROM liteshop_restaurant.restaurant_order WHERE tenant_id=920001) <> 722
    OR (SELECT COUNT(*) FROM liteshop_restaurant.restaurant_order_item WHERE tenant_id=920001) <> 1442
    OR (SELECT COUNT(*) FROM liteshop_restaurant.order_dish_change_log WHERE tenant_id=920001) <> 240
    OR (SELECT COUNT(*) FROM liteshop_restaurant.analysis_store_daily WHERE tenant_id=920001) <> 120
    OR (SELECT COUNT(*) FROM liteshop_user.saas_tenant_subscription WHERE tenant_id=920001 AND status='ACTIVE') <> 1
    OR NOT EXISTS (
      SELECT 1 FROM liteshop_user.saas_tenant_subscription subscription
        JOIN liteshop_user.saas_plan_entitlement entitlement
          ON entitlement.plan_version_id=subscription.plan_version_id
        JOIN liteshop_user.saas_feature feature ON feature.id=entitlement.feature_id
      WHERE subscription.tenant_id=920001 AND subscription.status='ACTIVE'
        AND feature.feature_code='ai_manager_calls' AND entitlement.enabled=1
        AND entitlement.quota_value=100 AND entitlement.reset_period='BILLING_CYCLE'
    )
    OR EXISTS (
      SELECT 1 FROM liteshop_restaurant.restaurant_order ro
      JOIN (SELECT tenant_id,order_id,SUM(unit_list_price*quantity) AS gross_amount,
        SUM(discount_amount) AS discount_amount,SUM(line_payable_amount) AS payable_amount
        FROM liteshop_restaurant.restaurant_order_item WHERE tenant_id=920001 GROUP BY tenant_id,order_id) items
        ON items.tenant_id=ro.tenant_id AND items.order_id=ro.id
      WHERE ro.tenant_id=920001 AND (ro.total_amount<>items.gross_amount
        OR ro.discount_amount<>items.discount_amount OR (ro.status<>6 AND ro.actual_amount<>items.payable_amount))
    )
    OR EXISTS (
      SELECT 1
      FROM `liteshop_restaurant`.`store_dish` sd
      LEFT JOIN `liteshop_restaurant`.`store_info` s
        ON s.id = sd.store_id AND s.tenant_id = sd.tenant_id
      LEFT JOIN `liteshop_restaurant`.`dish` d
        ON d.id = sd.dish_id AND d.tenant_id = sd.tenant_id
      WHERE sd.tenant_id = 920001 AND (s.id IS NULL OR d.id IS NULL)
    ) THEN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = '连锁测试数据关联或数量校验失败，本次导入已回滚。';
END IF;

COMMIT;
DROP TEMPORARY TABLE IF EXISTS tmp_current_demo_tenants;

SELECT t.id AS tenant_id, t.tenant_code, t.tenant_name,
       (SELECT COUNT(*) FROM `liteshop_user`.`sys_user` u WHERE u.tenant_id = t.id) AS users,
       (SELECT COUNT(*) FROM `liteshop_restaurant`.`store_info` s WHERE s.tenant_id = t.id) AS stores,
       (SELECT COUNT(*) FROM `liteshop_restaurant`.`dish` d WHERE d.tenant_id = t.id) AS dishes,
       (SELECT COUNT(*) FROM `liteshop_restaurant`.`store_dish` sd WHERE sd.tenant_id = t.id) AS store_dish_configs,
       (SELECT COUNT(*) FROM `liteshop_restaurant`.`menu_change_record` mc WHERE mc.tenant_id = t.id) AS menu_changes,
       (SELECT COUNT(*) FROM liteshop_restaurant.restaurant_order ro WHERE ro.tenant_id=t.id) AS orders,
       (SELECT COUNT(*) FROM liteshop_restaurant.analysis_store_daily a WHERE a.tenant_id=t.id) AS store_daily_rows
FROM `liteshop_user`.`sys_tenant` t
WHERE t.id = 920001;
END$$
DELIMITER ;

CALL `seed_current_demo_20260921`();
DROP PROCEDURE IF EXISTS `seed_current_demo_20260921`;

-- 登录信息：tenantCode=CHAIN_TEST，密码统一为 Admin@123456。
SELECT 'chain_admin' AS account, '总部管理员（全部门店、审批、分组和批量发布）' AS purpose
UNION ALL SELECT 'chain_flagship', '中心旗舰店管理员'
UNION ALL SELECT 'chain_airport', '机场高成本门店管理员'
UNION ALL SELECT 'chain_campus', '大学城自主定价门店管理员'
UNION ALL SELECT 'chain_franchise', '加盟示范店管理员'
UNION ALL SELECT 'chain_staff', '旗舰店员工'
UNION ALL SELECT 'chain_member', '普通会员';
