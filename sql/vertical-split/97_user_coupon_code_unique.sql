-- 用户券码由数据库保证租户内唯一。执行前先检查并处理历史重复券码。
-- SELECT tenant_id, code, COUNT(*) AS copies FROM user_coupon
-- WHERE code IS NOT NULL GROUP BY tenant_id, code HAVING COUNT(*) > 1;
-- 重复券码需先逐张核实核销记录并重新发码，避免直接覆盖仍在流通的券。
ALTER TABLE `user_coupon`
  DROP INDEX `idx_code`,
  ADD UNIQUE INDEX `uk_tenant_coupon_code` (`tenant_id`, `code`);

-- 核销记录与券码采用相同租户范围，避免其他租户恰好生成同一数码时互相阻断。
ALTER TABLE `verify_record`
  DROP INDEX `uk_verify_code`,
  ADD UNIQUE INDEX `uk_tenant_verify_code` (`tenant_id`, `verify_code`);
