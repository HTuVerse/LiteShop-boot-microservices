-- 在 liteshop_lottery 库执行。每个发放请求对每名用户最多入账一次。
CREATE TABLE IF NOT EXISTS lottery_chance_grant_receipt (
    tenant_id BIGINT NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    chances INT NOT NULL,
    applied TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, request_key, user_id),
    KEY idx_grant_pending (tenant_id, request_key, applied)
) ENGINE=InnoDB COMMENT='抽奖机会批量发放幂等凭据';
