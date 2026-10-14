-- 租户直连支付账户。私钥只存密文，门店不持有、不可见。
USE liteshop_config;

CREATE TABLE IF NOT EXISTS tenant_payment_account (
    id BIGINT NOT NULL COMMENT '支付账户ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    channel TINYINT NOT NULL COMMENT '渠道：1微信，2支付宝',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用：0否，1是',
    app_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '微信小程序或支付宝应用ID',
    mch_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '微信商户号，支付宝为空',
    mch_serial_no VARCHAR(128) NOT NULL DEFAULT '' COMMENT '微信商户API证书序列号，支付宝为空',
    private_key_ciphertext TEXT NOT NULL COMMENT '加密后的商户私钥PEM或支付宝应用私钥',
    api_v3_key_ciphertext TEXT NOT NULL COMMENT '加密后的微信APIv3密钥，支付宝为空',
    alipay_public_key_ciphertext TEXT NOT NULL COMMENT '加密后的支付宝公钥，微信为空',
    updated_by BIGINT NULL COMMENT '最后修改的租户管理员用户ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_channel (tenant_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户支付账户';
