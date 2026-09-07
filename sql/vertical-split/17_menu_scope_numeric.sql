-- MySQL 8：已执行旧版16脚本的数据库，执行本文件升级为数字枚举。
-- 暂停用户服务写入，执行后部署并重启新版用户服务、刷新前端。可重复执行。
-- 0=平台和租户，1=仅平台，2=仅租户；按钮/API仍允许NULL，表示随所属页面。
-- DDL隐式提交；异常数据会在转换前报错，不会静默改成0。
SET NAMES utf8mb4;
USE liteshop_user;
DROP PROCEDURE IF EXISTS migrate_menu_scope_numeric_20260905;
DELIMITER $$
CREATE PROCEDURE migrate_menu_scope_numeric_20260905()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='sys_permission' AND column_name='menu_scope') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='请先执行16_menu_scope.sql初始化菜单范围。';
  END IF;
  IF EXISTS(SELECT 1 FROM sys_permission WHERE menu_scope IS NOT NULL
    AND BINARY CAST(menu_scope AS CHAR) NOT IN ('0','1','2','ALL','PLATFORM','TENANT')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='menu_scope包含未知值，请核对后再迁移。';
  END IF;
  -- 先将旧字符串映射为数字文本，再改变字段类型，避免MySQL把字符串隐式转为0。
  UPDATE sys_permission SET menu_scope=CASE BINARY CAST(menu_scope AS CHAR)
    WHEN 'ALL' THEN 0 WHEN 'PLATFORM' THEN 1 WHEN 'TENANT' THEN 2
    ELSE menu_scope END WHERE menu_scope IS NOT NULL;
  ALTER TABLE sys_permission MODIFY COLUMN menu_scope TINYINT UNSIGNED NULL
    COMMENT '导航显示范围：0平台和租户，1仅平台，2仅租户；按钮继承页面';
END$$
DELIMITER ;
CALL migrate_menu_scope_numeric_20260905();
DROP PROCEDURE IF EXISTS migrate_menu_scope_numeric_20260905;
SELECT menu_scope,COUNT(*) AS menu_count FROM sys_permission GROUP BY menu_scope;
