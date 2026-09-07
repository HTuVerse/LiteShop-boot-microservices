-- ============================================================
-- LiteShop 空库开发种子数据
-- 前置条件：已依次执行 01_liteshop_user.sql ~ 06_liteshop_config.sql
-- 仅供本地开发/联调。生产环境不得使用本脚本中的账号和密码。
--
-- 所有预置账号密码：Admin@123456
-- 登录约定：
--   平台超管：tenantCode 不传，account=superadmin
--   租户账号：tenantCode=DEMO
-- ============================================================

START TRANSACTION;

USE `liteshop_user`;

-- 全局会员等级（不属于任何租户）
INSERT INTO `user_level`
(`id`, `level_code`, `level_name`, `required_exp`, `description`, `benefits`, `status`, `sort`)
VALUES
  (1, 1, '普通会员', 0, '注册后的默认等级', '["基础会员权益"]', 1, 1),
  (2, 2, '银卡会员', 1000, '累计经验达到 1000', '["会员活动优先参与"]', 1, 2),
  (3, 3, '金卡会员', 5000, '累计经验达到 5000', '["专属优惠券"]', 1, 3),
  (4, 4, '黑金会员', 20000, '累计经验达到 20000', '["专属优惠券","优先客服"]', 1, 4);

-- 全局菜单/权限定义。component_address 可按管理端实际目录继续调整。
INSERT INTO `sys_permission`
(`id`, `permission_name`, `permission_code`, `parent_id`, `permission_type`,
 `component_address`, `router_address`, `sort`, `status`, `icon`, `is_hide`, `is_iframe`, `keep_alive`)
VALUES
  (10, '平台管理', 'platform:menu', 0, 1, 'Layout', '/platform', 10, 1, 'Platform', 0, 0, 0),
  (11, '租户管理', 'platform:tenant', 10, 1, 'platform/tenant/index', '/platform/tenant', 11, 1, 'OfficeBuilding', 0, 0, 1),
  (12, '菜单管理', 'platform:permission', 10, 1, 'system/menu/index', '/platform/permission', 12, 1, 'Menu', 0, 0, 1),

  (20, '系统管理', 'system:menu', 0, 1, 'Layout', '/system', 20, 1, 'Setting', 0, 0, 0),
  (21, '用户管理', 'system:user', 20, 1, 'system/user/index', '/system/user', 21, 1, 'User', 0, 0, 1),
  (22, '角色管理', 'system:role', 20, 1, 'system/role/index', '/system/role', 22, 1, 'UserFilled', 0, 0, 1),
  (23, '门店授权', 'system:user-store', 20, 1, 'system/user-store/index', '/system/user-store', 23, 1, 'Connection', 0, 0, 1),

  (30, '餐饮管理', 'restaurant:menu', 0, 1, 'Layout', '/restaurant', 30, 1, 'KnifeFork', 0, 0, 0),
  (31, '门店管理', 'restaurant:store', 30, 1, 'restaurant/store/index', '/restaurant/store', 31, 1, 'Shop', 0, 0, 1),
  (32, '菜品分类', 'restaurant:category', 30, 1, 'restaurant/category/index', '/restaurant/category', 32, 1, 'Menu', 0, 0, 1),
  (33, '菜品管理', 'restaurant:dish', 30, 1, 'restaurant/dish/index', '/restaurant/dish', 33, 1, 'Dish', 0, 0, 1),
  (34, '餐桌管理', 'restaurant:table', 30, 1, 'restaurant/table/index', '/restaurant/table', 34, 1, 'Grid', 0, 0, 1),
  (35, '订单管理', 'restaurant:order', 30, 1, 'restaurant/order/index', '/restaurant/order', 35, 1, 'List', 0, 0, 1),
  (36, '打印管理', 'restaurant:printer', 30, 1, 'restaurant/printer/index', '/restaurant/printer', 36, 1, 'Printer', 0, 0, 1),
  (37, '经营分析', 'restaurant:analysis', 30, 1, 'restaurant/analysis/index', '/restaurant/analysis', 37, 1, 'DataAnalysis', 0, 0, 1),

  (40, '营销管理', 'marketing:menu', 0, 1, 'Layout', '/marketing', 40, 1, 'Present', 0, 0, 0),
  (41, '优惠券管理', 'marketing:coupon', 40, 1, 'marketing/coupon/index', '/marketing/coupon', 41, 1, 'Ticket', 0, 0, 1),
  (42, '抽奖管理', 'marketing:lottery', 40, 1, 'marketing/lottery/index', '/marketing/lottery', 42, 1, 'Trophy', 0, 0, 1),

  (50, '应用配置', 'config:menu', 0, 1, 'Layout', '/config', 50, 1, 'Tools', 0, 0, 0),
  (51, '基础配置', 'config:basic', 50, 1, 'config/basic/index', '/config/basic', 51, 1, 'Operation', 0, 0, 1),
  (52, '轮播图配置', 'config:banner', 50, 1, 'config/banner/index', '/config/banner', 52, 1, 'Picture', 0, 0, 1);

-- 平台角色与平台超级管理员（tenant_id 必须为 NULL）
INSERT INTO `sys_role`
(`id`, `role_name`, `role_code`, `description`, `status`, `tenant_id`)
VALUES (1, '平台超级管理员', 'SUPER_ADMIN', '平台级账号，可管理全部租户', 1, NULL);

INSERT INTO `sys_user`
(`id`, `username`, `password`, `nickname`, `gender`, `status`, `tenant_id`)
VALUES
  (1, 'superadmin', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu',
   '平台超级管理员', 0, 1, NULL);

INSERT INTO `sys_user_role` (`tenant_id`, `user_id`, `role_id`)
VALUES (NULL, 1, 1);

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT NULL, 1, `id` FROM `sys_permission` WHERE `status` = 1;

-- 演示租户与四类默认角色
INSERT INTO `sys_tenant`
(`id`, `tenant_code`, `tenant_name`, `contact_name`, `contact_phone`, `status`, `remark`)
VALUES (1001, 'DEMO', '演示商户', '演示联系人', '13800000000', 1, '本地开发联调用租户');

INSERT INTO `sys_role`
(`id`, `role_name`, `role_code`, `description`, `status`, `tenant_id`)
VALUES
  (1101, '商户管理员', 'ADMIN', '管理本租户全部门店与人员', 1, 1001),
  (1102, '门店管理员', 'STORE_ADMIN', '管理被授权门店', 1, 1001),
  (1103, '门店员工', 'STORE_STAFF', '操作被授权门店业务', 1, 1001),
  (1104, '普通用户', 'USER', '租户消费者', 1, 1001);

-- 演示账号；均使用脚本顶部的开发密码
INSERT INTO `sys_user`
(`id`, `username`, `password`, `nickname`, `gender`, `phone`, `status`, `tenant_id`)
VALUES
  (1001, 'tenant_admin', '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '商户管理员', 0, '13800000001', 1, 1001),
  (1002, 'store_admin',  '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '门店管理员', 0, '13800000002', 1, 1001),
  (1003, 'store_staff',  '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '门店员工', 0, '13800000003', 1, 1001),
  (1004, 'demo_user',    '$2a$10$a9g7B/z2QPtgQH7Cv2YqxeIOIu6WTkpDKzqWlm.Xsfcw.4IjyK2iu', '演示会员', 0, '13800000004', 1, 1001);

INSERT INTO `sys_user_role` (`tenant_id`, `user_id`, `role_id`)
VALUES
  (1001, 1001, 1101),
  (1001, 1002, 1102),
  (1001, 1003, 1103),
  (1001, 1004, 1104);

-- 商户管理员：除平台菜单外的全部权限
INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1001, 1101, `id` FROM `sys_permission`
WHERE `status` = 1 AND `permission_code` NOT LIKE 'platform:%';

-- 门店管理员：餐饮模块全部权限
INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1001, 1102, `id` FROM `sys_permission`
WHERE `status` = 1 AND `permission_code` LIKE 'restaurant:%';

-- 门店员工：餐饮根菜单、餐桌和订单
INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1001, 1103, `id` FROM `sys_permission`
WHERE `permission_code` IN ('restaurant:menu', 'restaurant:table', 'restaurant:order');

-- 门店管理员和员工只能访问被授权的演示门店
INSERT INTO `sys_user_store`
(`id`, `tenant_id`, `user_id`, `store_id`, `is_default`)
VALUES
  (1201, 1001, 1002, 2001, 1),
  (1202, 1001, 1003, 2001, 1);

USE `liteshop_restaurant`;

INSERT INTO `store_info`
(`id`, `store_name`, `store_code`, `address`, `phone`, `business_hours`,
 `payment_mode`, `pickup_mode`, `is_print`, `allow_pre_order`, `pre_order_limit`,
 `status`, `description`, `tenant_id`)
VALUES
  (2001, '演示商户一号店', 'DEMO_STORE', '开发环境演示地址', '13800000000', '09:00-22:00',
   1, 0, 0, 1, 10, 1, '用于验证租户与门店数据隔离', 1001);

USE `liteshop_config`;

INSERT INTO `sys_config`
(`id`, `config_key`, `config_value`, `config_type`, `config_group`,
 `config_name`, `description`, `editable`, `tenant_id`)
VALUES
  (3001, 'app.name', 'LiteShop 演示商户', 2, 2, '应用名称', '管理端和小程序展示名称', 1, 1001),
  (3002, 'app.logo', '', 1, 2, '应用 Logo', '应用 Logo 图片地址', 1, 1001),
  (3003, 'contact.phone', '13800000000', 2, 4, '客服电话', '租户客服电话', 1, 1001),
  (3004, 'theme.primaryColor', '#409EFF', 4, 3, '主题色', '管理端和小程序主色', 1, 1001),
  (3005, 'order.timeoutMinutes', '15', 5, 5, '订单超时分钟数', '未支付订单自动关闭时间', 1, 1001);

COMMIT;

-- 初始化后建议立即：
-- 1. 登录并修改全部开发账号密码；
-- 2. 根据前端真实组件目录调整 sys_permission.component_address；
-- 3. 创建第二个租户做交叉 ID 隔离测试。
