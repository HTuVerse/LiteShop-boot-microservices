-- AI 店长分析记录增加可查询的经营范围和实际周期。
ALTER TABLE saas_ai_generation
  ADD COLUMN created_by BIGINT NULL COMMENT '发起生成的用户ID' AFTER operation,
  ADD COLUMN scope_type VARCHAR(16) NULL COMMENT '经营范围：TENANT租户总部、STORE单门店' AFTER created_by,
  ADD COLUMN store_id BIGINT NULL COMMENT '单门店范围对应的门店ID' AFTER scope_type,
  ADD COLUMN time_range VARCHAR(16) NULL COMMENT '时间类型：TODAY、YESTERDAY、WEEK、MONTH' AFTER store_id,
  ADD COLUMN period_key VARCHAR(16) NULL COMMENT '实际周期键：日期、周一日期或年月' AFTER time_range,
  ADD KEY idx_saas_ai_history
    (tenant_id,scope_type,store_id,time_range,period_key,operation,created_at);

-- 已成功生成的旧记录从响应快照回填范围，新版发布后仍可在历史中查看。
UPDATE saas_ai_generation
SET scope_type = COALESCE(JSON_UNQUOTE(JSON_EXTRACT(response_json, '$.scope.scopeType')), 'TENANT'),
    store_id = CASE
      WHEN JSON_UNQUOTE(JSON_EXTRACT(response_json, '$.scope.scopeType')) = 'STORE'
      THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(response_json, '$.scope.storeId')) AS UNSIGNED)
      ELSE NULL
    END,
    time_range = COALESCE(JSON_UNQUOTE(JSON_EXTRACT(response_json, '$.scope.timeRange')), 'TODAY')
WHERE response_json IS NOT NULL;

UPDATE saas_ai_generation
SET period_key = CASE time_range
  WHEN 'YESTERDAY' THEN DATE_FORMAT(DATE_SUB(DATE(created_at), INTERVAL 1 DAY), '%Y-%m-%d')
  WHEN 'WEEK' THEN DATE_FORMAT(DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY), '%Y-%m-%d')
  WHEN 'MONTH' THEN DATE_FORMAT(created_at, '%Y-%m')
  ELSE DATE_FORMAT(created_at, '%Y-%m-%d')
END
WHERE scope_type IS NOT NULL AND time_range IS NOT NULL AND period_key IS NULL;
