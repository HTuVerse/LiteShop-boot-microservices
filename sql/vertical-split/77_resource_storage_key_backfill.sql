-- 在 76_storage_drivers.sql 后执行。仅回填可准确识别的历史随机 Key；原 URL 留作业务兼容依据。
-- 执行前请备份 liteshop_file.file_upload，并核对下面 SELECT 的结果。
USE `liteshop_file`;

SELECT `id`, `storage_type`, `storage_key`, `url`
FROM `file_upload`
WHERE `storage_key` IS NULL
ORDER BY `id`;

-- 升级前 storage_type 为空的记录，按 76 号迁移保存的旧驱动配置确认类型。
UPDATE `liteshop_file`.`file_upload` AS `file`
JOIN `liteshop_config`.`file_storage_profile` AS `profile`
  ON `profile`.`id` = `file`.`storage_profile_id`
SET `file`.`storage_type` = CASE
  WHEN JSON_UNQUOTE(JSON_EXTRACT(`profile`.`config_json`, '$.storageMode')) = 'QINIU' THEN 1
  ELSE 0 END
WHERE `file`.`storage_type` IS NULL;

-- 只接受完整的日期/随机文件名，避免从外部 URL 猜错对象 Key。
UPDATE `file_upload`
SET `storage_key` = SUBSTRING_INDEX(`url`, '/', -2),
    `file_extension` = LOWER(SUBSTRING_INDEX(`url`, '.', -1))
WHERE `storage_key` IS NULL
  AND `url` IS NOT NULL
  AND REGEXP_LIKE(`url`, '^https?://[^?#]+/[0-9]{8}/[[:xdigit:]]{32}[.][[:alnum:]]{1,10}$', 'i');

-- 剩余记录无法安全推导 Key，需人工核对；不要自动修改其 URL 或 storage_type。
SELECT `id`, `storage_type`, `url`
FROM `file_upload`
WHERE `storage_key` IS NULL
ORDER BY `id`;
