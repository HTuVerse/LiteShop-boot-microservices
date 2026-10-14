-- ============================================================
-- 清理旧开发/联调租户数据
-- 目标：DEMO、UI_TEST_A、UI_TEST_B，以及本脚本生成的 CHAIN_TEST。
-- 安全约束：租户 ID 与租户编码必须同时匹配才会删除，其他租户不受影响。
-- 执行前请停止业务服务和定时任务，并完成数据库备份。
-- ============================================================

USE `liteshop_user`;

DROP PROCEDURE IF EXISTS `cleanup_old_test_tenants_20260911`;
DELIMITER $$
CREATE PROCEDURE `cleanup_old_test_tenants_20260911`()
BEGIN
  DECLARE v_done tinyint DEFAULT 0;
  DECLARE v_schema varchar(64);
  DECLARE v_table varchar(64);
  DECLARE v_matched int DEFAULT 0;

  DECLARE tenant_tables CURSOR FOR
    SELECT c.TABLE_SCHEMA, c.TABLE_NAME
    FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t
      ON t.TABLE_SCHEMA = c.TABLE_SCHEMA
     AND t.TABLE_NAME = c.TABLE_NAME
     AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.COLUMN_NAME = 'tenant_id'
      AND c.TABLE_SCHEMA IN (
        'liteshop_user', 'liteshop_restaurant', 'liteshop_coupon',
        'liteshop_lottery', 'liteshop_file', 'liteshop_config', 'liteshop_message'
      )
    GROUP BY c.TABLE_SCHEMA, c.TABLE_NAME
    ORDER BY CASE WHEN c.TABLE_SCHEMA = 'liteshop_user' THEN 1 ELSE 0 END,
             c.TABLE_SCHEMA, c.TABLE_NAME;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'liteshop_user'
      AND TABLE_NAME = 'sys_tenant'
      AND COLUMN_NAME = 'tenant_code'
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '清理停止：sys_tenant.tenant_code 不存在，请先完成多租户结构升级。';
  END IF;

  DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_test_tenant`;
  CREATE TEMPORARY TABLE `tmp_cleanup_test_tenant` (
    `tenant_id` bigint NOT NULL PRIMARY KEY,
    `tenant_code` varchar(50) NOT NULL
  );

  -- ID 与编码双重匹配，防止固定 ID 已被其他正式租户占用时误删。
  INSERT INTO `tmp_cleanup_test_tenant` (`tenant_id`, `tenant_code`)
  SELECT `id`, `tenant_code`
  FROM `sys_tenant`
  WHERE (`id` = 1001 AND `tenant_code` = 'DEMO')
     OR (`id` = 910001 AND `tenant_code` = 'UI_TEST_A')
     OR (`id` = 910002 AND `tenant_code` = 'UI_TEST_B')
     OR (`id` = 920001 AND `tenant_code` = 'CHAIN_TEST');

  SELECT COUNT(*) INTO v_matched FROM `tmp_cleanup_test_tenant`;
  START TRANSACTION;

  -- 公告子表不全都有tenant_id，须在主表删除前按公告归属清理。
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='liteshop_message' AND TABLE_NAME='sys_announcement_target') THEN
    DELETE child FROM liteshop_message.sys_announcement_target child
      JOIN liteshop_message.sys_announcement a ON a.id=child.announcement_id
      JOIN tmp_cleanup_test_tenant t ON t.tenant_id=a.tenant_id;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='liteshop_message' AND TABLE_NAME='sys_announcement_attachment') THEN
    DELETE child FROM liteshop_message.sys_announcement_attachment child
      JOIN liteshop_message.sys_announcement a ON a.id=child.announcement_id
      JOIN tmp_cleanup_test_tenant t ON t.tenant_id=a.tenant_id;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='liteshop_message' AND TABLE_NAME='sys_announcement_read') THEN
    DELETE child FROM liteshop_message.sys_announcement_read child
      JOIN liteshop_message.sys_announcement a ON a.id=child.announcement_id
      JOIN tmp_cleanup_test_tenant t ON t.tenant_id=a.tenant_id;
  END IF;

  OPEN tenant_tables;
  cleanup_loop: LOOP
    FETCH tenant_tables INTO v_schema, v_table;
    IF v_done = 1 THEN
      LEAVE cleanup_loop;
    END IF;

    SET @cleanup_sql = CONCAT(
      'DELETE target FROM `', REPLACE(v_schema, '`', '``'), '`.`',
      REPLACE(v_table, '`', '``'), '` target ',
      'JOIN `liteshop_user`.`tmp_cleanup_test_tenant` selected ',
      'ON selected.tenant_id = target.tenant_id'
    );
    PREPARE cleanup_stmt FROM @cleanup_sql;
    EXECUTE cleanup_stmt;
    DEALLOCATE PREPARE cleanup_stmt;
  END LOOP;
  CLOSE tenant_tables;

  -- 删除订单的触发器可能重新写入队列，业务表清理完成后再删除这些刷新请求。
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='liteshop_restaurant' AND TABLE_NAME='analysis_refresh_queue') THEN
    DELETE q FROM liteshop_restaurant.analysis_refresh_queue q
    JOIN tmp_cleanup_test_tenant t ON t.tenant_id=q.tenant_id;
  END IF;

  DELETE tenant
  FROM `liteshop_user`.`sys_tenant` tenant
  JOIN `tmp_cleanup_test_tenant` selected ON selected.tenant_id = tenant.id;

  COMMIT;

  SELECT v_matched AS cleaned_tenants,
         'DEMO/UI_TEST_A/UI_TEST_B/CHAIN_TEST 中存在且 ID、编码匹配的租户已清理' AS result;
  DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_test_tenant`;
END$$
DELIMITER ;

CALL `cleanup_old_test_tenants_20260911`();
DROP PROCEDURE IF EXISTS `cleanup_old_test_tenants_20260911`;
