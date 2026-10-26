-- 退款渠道故障时使用持久化退避和人工核对标记，避免每分钟无限重试。
USE liteshop_restaurant;

ALTER TABLE order_refund
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0
        COMMENT '渠道异常后的自动对账重试次数',
    ADD COLUMN IF NOT EXISTS next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '下次允许自动对账的时间',
    ADD COLUMN IF NOT EXISTS review_required TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否需要人工核对：0否，1是',
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(500) NULL
        COMMENT '最近一次自动对账失败原因';
