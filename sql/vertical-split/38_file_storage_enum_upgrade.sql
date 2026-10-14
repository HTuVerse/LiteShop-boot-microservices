-- 文件服务存储类型由字符串枚举改为数值编码
-- 请在 liteshop_file 数据库执行；新版 05_liteshop_file.sql 初始化的空库无需执行。
SET @schema_name = DATABASE();
SET @ddl = IF((SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='file_upload' AND column_name='storage_type') IN ('varchar','char'),
  'UPDATE `file_upload` SET `storage_type`=CASE `storage_type` WHEN ''local'' THEN 0 WHEN ''LOCAL'' THEN 0 WHEN ''oss'' THEN 1 WHEN ''OSS'' THEN 1 ELSE `storage_type` END', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE `file_upload`
  MODIFY COLUMN `storage_type` tinyint NULL COMMENT '存储类型：0本地，1对象存储';
