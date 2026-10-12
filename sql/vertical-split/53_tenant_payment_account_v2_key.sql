-- 为租户支付账户增加微信 APIv2 密钥密文列，支撑微信付款码（被扫）支付。
-- 付款码走微信 v2 接口，与现有 JSAPI 的 v3 参数（证书序列号 + APIv3 密钥）不同，需单独存储。
USE liteshop_config;

ALTER TABLE tenant_payment_account
    ADD COLUMN api_v2_key_ciphertext TEXT NULL COMMENT '加密后的微信APIv2密钥（付款码v2接口用），支付宝为空';
