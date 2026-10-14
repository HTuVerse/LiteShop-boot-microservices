-- 平台 AI 模型配置，执行一次后可通过后台替换模型，无需重复发布 Nacos。
USE liteshop_config;
CREATE TABLE IF NOT EXISTS sys_ai_model_config (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '平台单例配置ID，固定为1',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '配置版本，0表示尚未通过后台保存',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用AI模型：0关闭，1启用',
    base_url VARCHAR(500) NOT NULL DEFAULT 'https://api.openai.com/v1' COMMENT '兼容接口基础地址',
    model_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '供应商模型标识',
    api_key_ciphertext TEXT NOT NULL COMMENT 'AES-GCM加密后的API Key，空值表示未配置',
    updated_by BIGINT NULL COMMENT '最后修改的超级管理员用户ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台AI模型运行配置';
INSERT INTO sys_ai_model_config (id, api_key_ciphertext) VALUES (1, '') ON DUPLICATE KEY UPDATE id = id;
