-- 余额支付扣款和超时取消在用户库中以订单号串行，避免迟到请求继续扣款。
CREATE TABLE IF NOT EXISTS `balance_payment_fence` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `order_no` varchar(64) NOT NULL COMMENT '餐饮订单号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0允许扣款，1已封闭',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`tenant_id`, `order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='余额支付订单号栅栏';
