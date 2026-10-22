-- 用户库：记录会员经验的每一次实际变动。
USE liteshop_user;

CREATE TABLE IF NOT EXISTS user_exp_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '经验流水ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  user_id BIGINT NOT NULL COMMENT '会员ID',
  change_exp INT NOT NULL COMMENT '本次经验变化量；正数增加，负数减少',
  before_exp INT NOT NULL COMMENT '变动前经验',
  after_exp INT NOT NULL COMMENT '变动后经验',
  source VARCHAR(32) NOT NULL COMMENT '来源：ORDER_REWARD订单奖励、ORDER_REFUND退款撤回、MANUAL人工调整、LEVEL_ADJUST等级调整、OPENING_BALANCE历史结转',
  order_no VARCHAR(80) NULL COMMENT '关联餐饮订单号',
  remark VARCHAR(500) NULL COMMENT '变动说明',
  operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变动时间',
  PRIMARY KEY (id),
  KEY idx_user_exp_time (tenant_id, user_id, operation_time, id),
  KEY idx_user_exp_order (tenant_id, order_no)
) ENGINE=InnoDB COMMENT='会员经验成长明细';

-- 上线前已积累的经验无法还原逐笔来源，只为尚无流水的会员记录期初余额。
INSERT INTO user_exp_record (tenant_id,user_id,change_exp,before_exp,after_exp,source,remark,operation_time)
SELECT u.tenant_id,u.id,u.exp,0,u.exp,'OPENING_BALANCE','历史经验结转（明细上线前）',NOW()
FROM sys_user u
WHERE u.exp > 0
  AND NOT EXISTS (SELECT 1 FROM user_exp_record r WHERE r.tenant_id=u.tenant_id AND r.user_id=u.id);
