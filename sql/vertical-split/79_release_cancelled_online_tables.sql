USE `liteshop_restaurant`;

-- 修复旧版本中线上未付订单已取消、桌位会话却仍在进行中的数据。
-- 仅处理该会话所有订单均已取消的情况，避免释放正在使用的桌位。
UPDATE `table_session` session
SET session.`status` = 2,
    session.`end_time` = COALESCE(session.`end_time`, NOW()),
    session.`current_order_id` = NULL,
    session.`paid_amount` = 0,
    session.`total_orders` = 0,
    session.`total_amount` = 0,
    session.`updated_at` = NOW()
WHERE session.`status` = 1
  AND EXISTS (
    SELECT 1 FROM `restaurant_order` orders
    WHERE orders.`tenant_id` = session.`tenant_id`
      AND orders.`session_no` = session.`session_no`
      AND orders.`payment_mode` = 1
      AND orders.`status` = 6
  )
  AND NOT EXISTS (
    SELECT 1 FROM `restaurant_order` active_order
    WHERE active_order.`tenant_id` = session.`tenant_id`
      AND active_order.`session_no` = session.`session_no`
      AND active_order.`status` <> 6
  );

UPDATE `table_info` table_row
SET table_row.`status` = 0,
    table_row.`updated_at` = NOW()
WHERE table_row.`status` = 1
  AND EXISTS (
    SELECT 1 FROM `table_session` session
    JOIN `restaurant_order` orders
      ON orders.`tenant_id` = session.`tenant_id`
     AND orders.`session_no` = session.`session_no`
    WHERE session.`tenant_id` = table_row.`tenant_id`
      AND session.`table_id` = table_row.`id`
      AND session.`status` = 2
      AND orders.`payment_mode` = 1
      AND orders.`status` = 6
  )
  AND NOT EXISTS (
    SELECT 1 FROM `table_session` active_session
    WHERE active_session.`tenant_id` = table_row.`tenant_id`
      AND active_session.`table_id` = table_row.`id`
      AND active_session.`status` = 1
  );
