-- ============================================================
-- 数据迁移脚本：hongtu（单库） -> 6 个按服务拆分的新库
--
-- 使用步骤（停机迁移）：
--   1. 停止所有微服务
--   2. 依次执行 01~06 建库脚本
--   3. 执行本脚本第一部分【迁移前检查】，确认所有查询返回 0 行
--      （新表个别 varchar 列做了收窄，若有超长数据需先处理）
--   4. 执行第二部分【数据迁移】
--   5. 执行第三部分【行数核对】，确认新旧库行数一致
--
-- 说明：
--   - 大多数新表与旧表列顺序一致，仅在末尾追加 tenant_id，使用 SELECT *, 1 迁移；
--     打印机、打印模板和文件记录因枚举数值化使用显式列清单与 CASE 转换
--   - sys_permission / sys_role_permission / sys_user_role / user_level
--     为全局表（无 tenant_id），直接 SELECT *
--   - 存量自增 ID 原样保留（很小，不会与雪花 ID 冲突），新数据由雪花算法生成
-- ============================================================

-- ============================================================
-- 一、迁移前检查（每条都应返回 0 行，否则先修数据）
-- ============================================================
SELECT id, phone       FROM hongtu.sys_user            WHERE CHAR_LENGTH(phone) > 20;
SELECT id, email       FROM hongtu.sys_user            WHERE CHAR_LENGTH(email) > 100;
SELECT id, open_id     FROM hongtu.sys_user            WHERE CHAR_LENGTH(open_id) > 64;
SELECT id, username    FROM hongtu.user_points_record  WHERE CHAR_LENGTH(username) > 50;
SELECT id, phone       FROM hongtu.store_info          WHERE CHAR_LENGTH(phone) > 20;
SELECT id, pickup_code FROM hongtu.restaurant_order    WHERE CHAR_LENGTH(pickup_code) > 32;
SELECT id, printer_brand FROM hongtu.printer_info
WHERE printer_brand IS NOT NULL AND printer_brand NOT IN ('1','2','FEIER','飞鹅','OTHER','其它','其他');
SELECT id, paper_width FROM hongtu.print_template
WHERE paper_width IS NOT NULL AND paper_width NOT IN ('0','1','58mm','80mm');
SELECT id, content_type  FROM hongtu.file_upload       WHERE CHAR_LENGTH(content_type) > 128;
SELECT id, storage_type FROM hongtu.file_upload
WHERE storage_type IS NOT NULL AND storage_type NOT IN ('0','1','local','LOCAL','oss','OSS');
-- activity_prize 新表以 (activity_id, prize_id) 为主键，检查是否有重复行
SELECT activity_id, prize_id, COUNT(*) c FROM hongtu.activity_prize
GROUP BY activity_id, prize_id HAVING c > 1;

-- ============================================================
-- 二、数据迁移
-- ============================================================

-- ---------- liteshop_user ----------
-- 默认租户：存量数据统一归属租户 1
INSERT INTO liteshop_user.sys_tenant (id, tenant_name, status, remark)
VALUES (1, '默认商户', 1, '单体迁移时的默认租户，存量数据全部归属该租户');

INSERT INTO liteshop_user.sys_user             SELECT *, 1 FROM hongtu.sys_user;
INSERT INTO liteshop_user.sys_role             SELECT *, 1 FROM hongtu.sys_role;
INSERT INTO liteshop_user.sys_permission       SELECT *    FROM hongtu.sys_permission;
INSERT INTO liteshop_user.sys_role_permission  SELECT *    FROM hongtu.sys_role_permission;
INSERT INTO liteshop_user.sys_user_role        SELECT *    FROM hongtu.sys_user_role;
INSERT INTO liteshop_user.user_level           SELECT *    FROM hongtu.user_level;
INSERT INTO liteshop_user.user_points_record   SELECT *, 1 FROM hongtu.user_points_record;
INSERT INTO liteshop_user.user_surplus_record  SELECT *, 1 FROM hongtu.user_surplus_record;

-- ---------- liteshop_restaurant ----------
INSERT INTO liteshop_restaurant.store_info            SELECT *, 1 FROM hongtu.store_info;
INSERT INTO liteshop_restaurant.dish_category         SELECT *, 1 FROM hongtu.dish_category;
INSERT INTO liteshop_restaurant.dish                  SELECT *, 1 FROM hongtu.dish;
INSERT INTO liteshop_restaurant.dish_spec_option      SELECT *, 1 FROM hongtu.dish_spec_option;
INSERT INTO liteshop_restaurant.table_info            SELECT *, 1 FROM hongtu.table_info;
INSERT INTO liteshop_restaurant.table_session         SELECT *, 1 FROM hongtu.table_session;
INSERT INTO liteshop_restaurant.restaurant_order      SELECT *, 1 FROM hongtu.restaurant_order;
INSERT INTO liteshop_restaurant.restaurant_order_item SELECT *, 1 FROM hongtu.restaurant_order_item;
INSERT INTO liteshop_restaurant.printer_info
  (id, sn, printer_key, printer_brand, remark, card_num, store_id, template_id, printer_type,
   status, last_online_time, is_default, enabled, created_at, updated_at, tenant_id)
SELECT id, sn, printer_key,
       CASE printer_brand WHEN 'FEIER' THEN 1 WHEN '飞鹅' THEN 1
         WHEN 'OTHER' THEN 2 WHEN '其它' THEN 2 WHEN '其他' THEN 2
         ELSE CAST(printer_brand AS UNSIGNED) END,
       remark, card_num, store_id, template_id, printer_type, status, last_online_time,
       is_default, enabled, created_at, updated_at, 1
FROM hongtu.printer_info;

INSERT INTO liteshop_restaurant.print_template
  (id, template_name, store_id, paper_width, show_logo, logo_url, store_name_style,
   show_address, show_phone, show_business_hours, order_info_fields,
   show_dish_image, show_dish_unit_price, show_dish_quantity, show_dish_amount,
   show_dish_spec, show_dish_original_price, show_dish_discount_price, show_dish_note,
   show_order_note, show_payment_info,
   footer_text, show_qrcode, qrcode_template, qrcode_size, font_size, print_density,
   print_copies, is_default, enabled, remark, created_at, updated_at, show_pickup_code, tenant_id)
SELECT id, template_name, store_id,
       CASE paper_width WHEN '58mm' THEN 0 WHEN '80mm' THEN 1 ELSE CAST(paper_width AS UNSIGNED) END,
       show_logo, logo_url, store_name_style, show_address, show_phone, show_business_hours,
       order_info_fields, show_dish_image, 1, 1, 1,
       CASE WHEN dish_list_style = 1 THEN show_dish_spec ELSE 0 END, 1, 1,
       CASE WHEN dish_list_style = 1 THEN show_dish_note ELSE 0 END,
       show_order_note, show_payment_info, footer_text, show_qrcode, qrcode_template,
       qrcode_size, font_size, print_density, print_copies, is_default, enabled, remark,
       created_at, updated_at, show_pickup_code, 1
FROM hongtu.print_template;

-- ---------- liteshop_coupon ----------
INSERT INTO liteshop_coupon.coupon           SELECT *, 1 FROM hongtu.coupon;
INSERT INTO liteshop_coupon.coupon_dish_item SELECT *, 1 FROM hongtu.coupon_dish_item;
INSERT INTO liteshop_coupon.user_coupon      SELECT *, 1 FROM hongtu.user_coupon;
INSERT INTO liteshop_coupon.verify_record
       (id, user_coupon_id, merchant_id, verify_code, verify_time, status, tenant_id)
SELECT id, user_coupon_id, merchant_id, verify_code, verify_time, status, 1
FROM hongtu.verify_record;

-- ---------- liteshop_lottery ----------
INSERT INTO liteshop_lottery.lottery_activity     SELECT *, 1 FROM hongtu.lottery_activity;
INSERT INTO liteshop_lottery.lottery_prize        SELECT *, 1 FROM hongtu.lottery_prize;
INSERT INTO liteshop_lottery.activity_prize       SELECT *, 1 FROM hongtu.activity_prize;
INSERT INTO liteshop_lottery.lottery_record       SELECT *, 1 FROM hongtu.lottery_record;
INSERT INTO liteshop_lottery.user_activity_chance SELECT *, 1 FROM hongtu.user_activity_chance;

-- ---------- liteshop_file（新表补了 created_at，需显式列清单） ----------
INSERT INTO liteshop_file.file_upload
  (id, original_file_name, size, content_type, url, storage_type)
SELECT id, original_file_name, size, content_type, url,
       CASE storage_type WHEN 'local' THEN 0 WHEN 'LOCAL' THEN 0
         WHEN 'oss' THEN 1 WHEN 'OSS' THEN 1 ELSE CAST(storage_type AS UNSIGNED) END
FROM hongtu.file_upload;

-- ---------- liteshop_config ----------
INSERT INTO liteshop_config.sys_config SELECT *, 1 FROM hongtu.sys_config;
INSERT INTO liteshop_config.sys_banner SELECT *, 1 FROM hongtu.sys_banner;

-- ============================================================
-- 三、行数核对（每行 old = new 才算迁移成功）
-- ============================================================
SELECT 'sys_user' t, (SELECT COUNT(*) FROM hongtu.sys_user) old_cnt, (SELECT COUNT(*) FROM liteshop_user.sys_user) new_cnt
UNION ALL SELECT 'sys_role', (SELECT COUNT(*) FROM hongtu.sys_role), (SELECT COUNT(*) FROM liteshop_user.sys_role)
UNION ALL SELECT 'sys_permission', (SELECT COUNT(*) FROM hongtu.sys_permission), (SELECT COUNT(*) FROM liteshop_user.sys_permission)
UNION ALL SELECT 'sys_role_permission', (SELECT COUNT(*) FROM hongtu.sys_role_permission), (SELECT COUNT(*) FROM liteshop_user.sys_role_permission)
UNION ALL SELECT 'sys_user_role', (SELECT COUNT(*) FROM hongtu.sys_user_role), (SELECT COUNT(*) FROM liteshop_user.sys_user_role)
UNION ALL SELECT 'user_level', (SELECT COUNT(*) FROM hongtu.user_level), (SELECT COUNT(*) FROM liteshop_user.user_level)
UNION ALL SELECT 'user_points_record', (SELECT COUNT(*) FROM hongtu.user_points_record), (SELECT COUNT(*) FROM liteshop_user.user_points_record)
UNION ALL SELECT 'user_surplus_record', (SELECT COUNT(*) FROM hongtu.user_surplus_record), (SELECT COUNT(*) FROM liteshop_user.user_surplus_record)
UNION ALL SELECT 'store_info', (SELECT COUNT(*) FROM hongtu.store_info), (SELECT COUNT(*) FROM liteshop_restaurant.store_info)
UNION ALL SELECT 'dish_category', (SELECT COUNT(*) FROM hongtu.dish_category), (SELECT COUNT(*) FROM liteshop_restaurant.dish_category)
UNION ALL SELECT 'dish', (SELECT COUNT(*) FROM hongtu.dish), (SELECT COUNT(*) FROM liteshop_restaurant.dish)
UNION ALL SELECT 'dish_spec_option', (SELECT COUNT(*) FROM hongtu.dish_spec_option), (SELECT COUNT(*) FROM liteshop_restaurant.dish_spec_option)
UNION ALL SELECT 'table_info', (SELECT COUNT(*) FROM hongtu.table_info), (SELECT COUNT(*) FROM liteshop_restaurant.table_info)
UNION ALL SELECT 'table_session', (SELECT COUNT(*) FROM hongtu.table_session), (SELECT COUNT(*) FROM liteshop_restaurant.table_session)
UNION ALL SELECT 'restaurant_order', (SELECT COUNT(*) FROM hongtu.restaurant_order), (SELECT COUNT(*) FROM liteshop_restaurant.restaurant_order)
UNION ALL SELECT 'restaurant_order_item', (SELECT COUNT(*) FROM hongtu.restaurant_order_item), (SELECT COUNT(*) FROM liteshop_restaurant.restaurant_order_item)
UNION ALL SELECT 'printer_info', (SELECT COUNT(*) FROM hongtu.printer_info), (SELECT COUNT(*) FROM liteshop_restaurant.printer_info)
UNION ALL SELECT 'print_template', (SELECT COUNT(*) FROM hongtu.print_template), (SELECT COUNT(*) FROM liteshop_restaurant.print_template)
UNION ALL SELECT 'coupon', (SELECT COUNT(*) FROM hongtu.coupon), (SELECT COUNT(*) FROM liteshop_coupon.coupon)
UNION ALL SELECT 'coupon_dish_item', (SELECT COUNT(*) FROM hongtu.coupon_dish_item), (SELECT COUNT(*) FROM liteshop_coupon.coupon_dish_item)
UNION ALL SELECT 'user_coupon', (SELECT COUNT(*) FROM hongtu.user_coupon), (SELECT COUNT(*) FROM liteshop_coupon.user_coupon)
UNION ALL SELECT 'verify_record', (SELECT COUNT(*) FROM hongtu.verify_record), (SELECT COUNT(*) FROM liteshop_coupon.verify_record)
UNION ALL SELECT 'lottery_activity', (SELECT COUNT(*) FROM hongtu.lottery_activity), (SELECT COUNT(*) FROM liteshop_lottery.lottery_activity)
UNION ALL SELECT 'lottery_prize', (SELECT COUNT(*) FROM hongtu.lottery_prize), (SELECT COUNT(*) FROM liteshop_lottery.lottery_prize)
UNION ALL SELECT 'activity_prize', (SELECT COUNT(*) FROM hongtu.activity_prize), (SELECT COUNT(*) FROM liteshop_lottery.activity_prize)
UNION ALL SELECT 'lottery_record', (SELECT COUNT(*) FROM hongtu.lottery_record), (SELECT COUNT(*) FROM liteshop_lottery.lottery_record)
UNION ALL SELECT 'user_activity_chance', (SELECT COUNT(*) FROM hongtu.user_activity_chance), (SELECT COUNT(*) FROM liteshop_lottery.user_activity_chance)
UNION ALL SELECT 'file_upload', (SELECT COUNT(*) FROM hongtu.file_upload), (SELECT COUNT(*) FROM liteshop_file.file_upload)
UNION ALL SELECT 'sys_config', (SELECT COUNT(*) FROM hongtu.sys_config), (SELECT COUNT(*) FROM liteshop_config.sys_config)
UNION ALL SELECT 'sys_banner', (SELECT COUNT(*) FROM hongtu.sys_banner), (SELECT COUNT(*) FROM liteshop_config.sys_banner);
