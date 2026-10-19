-- 租户配置库新增飞鹅出餐宝扫码结果解密凭据；新旧部署均在建表后执行。
USE `liteshop_config`;

ALTER TABLE `tenant_runtime_config`
  ADD COLUMN `printer_scan_private_key_ciphertext` text NULL
    COMMENT '加密后的飞鹅扫码结果 RSA 解密私钥' AFTER `printer_public_key`;
