-- 租户储值档位和充值订单与会员余额同属用户域。
USE `liteshop_user`;
CREATE TABLE IF NOT EXISTS `wallet_recharge_plan` (
  `id` bigint NOT NULL COMMENT '储值档位ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `amount` decimal(10,2) NOT NULL COMMENT '实际支付金额，元',
  `bonus` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '额外赠送余额，元',
  `badge` varchar(30) NULL COMMENT '小程序档位角标',
  `sort` int NOT NULL DEFAULT 0 COMMENT '展示排序，越大越靠前',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0停用，1启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_wallet_plan_tenant` (`tenant_id`,`status`,`sort`),
  CONSTRAINT `chk_wallet_plan_amount` CHECK (`amount` > 0 AND `bonus` >= 0)
) ENGINE=InnoDB COMMENT='租户会员储值档位';

CREATE TABLE IF NOT EXISTS `wallet_recharge_order` (
  `id` bigint NOT NULL COMMENT '储值订单ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '充值会员ID',
  `plan_id` bigint NOT NULL COMMENT '下单时档位ID',
  `amount` decimal(10,2) NOT NULL COMMENT '实付金额快照，元',
  `bonus` decimal(10,2) NOT NULL COMMENT '赠送余额快照，元',
  `status` varchar(20) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID待支付、PAID待入账、SUCCESS已入账、CLOSED已关闭',
  `request_key` varchar(64) NOT NULL COMMENT '客户端请求幂等键',
  `out_trade_no` varchar(64) NOT NULL COMMENT '微信商户订单号',
  `payment_txn_id` varchar(64) NULL COMMENT '微信交易号',
  `expires_at` datetime NOT NULL COMMENT '支付截止时间',
  `paid_at` datetime NULL COMMENT '渠道支付时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_request` (`tenant_id`,`user_id`,`request_key`),
  UNIQUE KEY `uk_wallet_trade` (`tenant_id`,`out_trade_no`),
  KEY `idx_wallet_order_tenant` (`tenant_id`,`created_at`),
  KEY `idx_wallet_retry` (`status`,`updated_at`)
) ENGINE=InnoDB COMMENT='租户会员储值订单';

ALTER TABLE `sys_user` ADD COLUMN `gift_money` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '剩余赠送余额，元；包含在 surplus_money 中';
ALTER TABLE `user_surplus_record`
  ADD COLUMN `gift_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '本次变动中的赠送余额金额，元',
  ADD COLUMN `source_order_no` varchar(64) NULL COMMENT '退款关联的原余额支付订单号';
ALTER TABLE `user_surplus_record` ADD KEY `idx_surplus_source_order` (`tenant_id`,`source_order_no`);
INSERT INTO `sys_permission` (`id`,`permission_name`,`permission_code`,`parent_id`,`permission_type`,
 `component_address`,`router_address`,`sort`,`status`,`icon`,`is_hide`,`is_iframe`,`keep_alive`)
SELECT 900120,'储值管理','marketing:wallet',p.id,1,
 'activity/wallet/index','/marketing/wallet',44,1,'Wallet',0,0,1
FROM `sys_permission` p WHERE p.permission_code='marketing:menu'
 AND NOT EXISTS (SELECT 1 FROM `sys_permission` x WHERE x.permission_code='marketing:wallet');
INSERT INTO `sys_role_permission` (`tenant_id`,`role_id`,`permission_id`)
SELECT DISTINCT rp.tenant_id,rp.role_id,m.id FROM `sys_role_permission` rp
JOIN `sys_permission` c ON c.id=rp.permission_id AND c.permission_code='marketing:points-mall'
JOIN `sys_permission` m ON m.permission_code='marketing:wallet'
WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` x
 WHERE x.tenant_id <=> rp.tenant_id AND x.role_id=rp.role_id AND x.permission_id=m.id);
