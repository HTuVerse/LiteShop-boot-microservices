-- 退款单、销量确认标记，以及支付状态扩展：7对账中，8已收款待处理。
USE liteshop_restaurant;

ALTER TABLE restaurant_order
    ADD COLUMN IF NOT EXISTS sales_committed TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '销量是否已计入：0否，1是。未支付只预占库存，支付成功后才加销量'
        AFTER stock_reserved;

ALTER TABLE restaurant_order
    MODIFY COLUMN payment_status TINYINT NOT NULL DEFAULT 0
        COMMENT '支付状态：0未支付，1已支付，2部分退款，3全额退款，4支付超时，5取消支付，6支付失败，7对账中，8已收款待处理';

-- 升级前已预占的订单沿用旧逻辑，销量已在扣库存时计入，避免支付成功再次累加。
UPDATE restaurant_order
SET sales_committed = 1
WHERE stock_reserved = 1
  AND sales_committed = 0;

CREATE TABLE IF NOT EXISTS order_refund (
    id BIGINT NOT NULL COMMENT '退款单ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    store_id BIGINT NOT NULL COMMENT '门店ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '原商户订单号',
    refund_no VARCHAR(64) NOT NULL COMMENT '服务端退款单号，同时作为渠道幂等号',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '客户端退菜幂等键',
    channel TINYINT NOT NULL COMMENT '原支付方式：1微信，2支付宝，3现金，4银行卡，5线下，6余额，7优惠券',
    amount DECIMAL(10, 2) NOT NULL COMMENT '退款金额，单位：元',
    status TINYINT NOT NULL COMMENT '退款状态：0已申请，1处理中，2成功，3失败',
    channel_refund_id VARCHAR(64) NULL COMMENT '渠道退款单号',
    reason VARCHAR(255) NULL COMMENT '退款原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_refund_no (tenant_id, refund_no),
    UNIQUE KEY uk_tenant_order_idem (tenant_id, order_id, idempotency_key),
    KEY idx_refund_status (tenant_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单退款单';

CREATE TABLE IF NOT EXISTS order_refund_item (
    id BIGINT NOT NULL COMMENT '退款明细ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    refund_id BIGINT NOT NULL COMMENT '退款单ID',
    order_item_id BIGINT NOT NULL COMMENT '订单项ID',
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    quantity INT NOT NULL COMMENT '退菜数量',
    amount DECIMAL(10, 2) NOT NULL COMMENT '该行退款金额，单位：元',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_refund_item (tenant_id, refund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单退款明细';
