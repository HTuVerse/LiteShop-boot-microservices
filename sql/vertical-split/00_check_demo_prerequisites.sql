-- 模拟数据前置检查：只查询，不删除数据、不修改结构。
-- 返回缺少项目及关联脚本；升级脚本不能盲目重跑，请先根据结果确认缺项。
WITH required_tables AS (
  SELECT 'liteshop_message' AS db_name, 'sys_announcement' AS table_name, '26_message_announcements.sql' AS related_sql
  UNION ALL SELECT 'liteshop_message','sys_announcement_target','26_message_announcements.sql'
  UNION ALL SELECT 'liteshop_message','sys_announcement_attachment','27_announcement_richtext_attachments.sql'
  UNION ALL SELECT 'liteshop_message','sys_announcement_read','26_message_announcements.sql'
  UNION ALL SELECT 'liteshop_restaurant','analysis_store_daily','42_tenant_business_analysis.sql'
  UNION ALL SELECT 'liteshop_restaurant','analysis_store_dish_daily','42_tenant_business_analysis.sql'
  UNION ALL SELECT 'liteshop_restaurant','analysis_refresh_queue','44_tenant_analysis_dirty_queue.sql'
  UNION ALL SELECT 'liteshop_restaurant','order_dish_change_log','41_add_order_dish_change_log.sql'
  UNION ALL SELECT 'liteshop_restaurant','order_price_adjustment','35_pricing_center_breaking_upgrade.sql'
  UNION ALL SELECT 'liteshop_restaurant','table_reservation','19_table_reservation_details.sql'
  UNION ALL SELECT 'liteshop_user','user_level_scheme','12_member_level_schemes.sql'
), required_columns AS (
  SELECT 'liteshop_restaurant' AS db_name, 'restaurant_order_item' AS table_name,
    'unit_cost_snapshot' AS column_name, '42_tenant_business_analysis.sql' AS related_sql
  UNION ALL SELECT 'liteshop_restaurant','pricing_campaign_scope','store_group_id','39_pricing_store_group_scope.sql'
  UNION ALL SELECT 'liteshop_user','sys_permission','menu_scope','16_menu_scope.sql'
), required_menus AS (
  SELECT 'restaurant:pricing' AS permission_code, '36_pricing_permissions.sql' AS related_sql
  UNION ALL SELECT 'restaurant:tenant-analysis','43_tenant_analysis_menu.sql'
  UNION ALL SELECT 'operations:assistant','11_add_database_driven_menus.sql / 45_ai_manager_phase1.sql'
  UNION ALL SELECT 'operations:direct-cashier','11_add_database_driven_menus.sql'
  UNION ALL SELECT 'operations:table-cashier','11_add_database_driven_menus.sql'
)
SELECT CONCAT('缺少表：',r.db_name,'.',r.table_name) AS missing_item, r.related_sql
FROM required_tables r
WHERE NOT EXISTS (SELECT 1 FROM information_schema.TABLES t
  WHERE t.TABLE_SCHEMA=r.db_name AND t.TABLE_NAME=r.table_name)
UNION ALL
SELECT CONCAT('缺少字段：',r.db_name,'.',r.table_name,'.',r.column_name),r.related_sql
FROM required_columns r
WHERE NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c
  WHERE c.TABLE_SCHEMA=r.db_name AND c.TABLE_NAME=r.table_name AND c.COLUMN_NAME=r.column_name)
UNION ALL
SELECT CONCAT('菜单缺少或停用：',r.permission_code),r.related_sql
FROM required_menus r
WHERE NOT EXISTS (SELECT 1 FROM liteshop_user.sys_permission p
  WHERE p.permission_code=r.permission_code AND p.status=1)
UNION ALL
SELECT '缺少已发布专业版套餐v1','25_saas_restaurant_plans.sql'
WHERE NOT EXISTS (SELECT 1 FROM liteshop_user.saas_plan_version v
  JOIN liteshop_user.saas_plan p ON p.id=v.plan_id
  WHERE p.plan_code='pro' AND v.version_no=1 AND v.status='PUBLISHED');
