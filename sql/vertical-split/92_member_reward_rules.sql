-- 会员奖励规则随等级方案维护，未配置的方案不自动发放。
USE liteshop_user;

CREATE TABLE IF NOT EXISTS member_reward_rule (
  scheme_id BIGINT NOT NULL COMMENT '所属会员等级方案ID',
  tenant_id BIGINT NULL COMMENT '所属租户ID；空值表示平台模板',
  exp_per_yuan DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '每实付1元获得的基础经验',
  points_per_yuan DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '每实付1元获得的基础积分',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (scheme_id)
) COMMENT='会员餐饮订单奖励基础规则';

CREATE TABLE IF NOT EXISTS member_reward_level_multiplier (
  scheme_id BIGINT NOT NULL COMMENT '所属会员等级方案ID',
  level_code INT NOT NULL COMMENT '会员等级编码',
  tenant_id BIGINT NULL COMMENT '所属租户ID；空值表示平台模板',
  exp_multiplier DECIMAL(8,4) NOT NULL DEFAULT 1 COMMENT '经验获取倍率',
  points_multiplier DECIMAL(8,4) NOT NULL DEFAULT 1 COMMENT '积分获取倍率',
  PRIMARY KEY (scheme_id, level_code)
) COMMENT='会员各等级餐饮订单奖励倍率';

CREATE TABLE IF NOT EXISTS member_order_reward (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '奖励记录ID',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  order_no VARCHAR(80) NOT NULL COMMENT '餐饮订单号',
  user_id BIGINT NOT NULL COMMENT '获奖会员ID',
  scheme_id BIGINT NOT NULL COMMENT '发放时生效的等级方案ID',
  level_code INT NOT NULL COMMENT '首次发放时的会员等级编码',
  exp_rate DECIMAL(12,4) NOT NULL COMMENT '首次发放时每元基础经验快照',
  points_rate DECIMAL(12,4) NOT NULL COMMENT '首次发放时每元基础积分快照',
  exp_multiplier DECIMAL(8,4) NOT NULL COMMENT '首次发放时经验倍率快照',
  points_multiplier DECIMAL(8,4) NOT NULL COMMENT '首次发放时积分倍率快照',
  remaining_amount DECIMAL(14,2) NOT NULL COMMENT '最新可奖励实付金额，单位元',
  awarded_exp INT NOT NULL COMMENT '当前订单净发放经验',
  awarded_points INT NOT NULL COMMENT '当前订单净发放积分',
  created_at DATETIME NOT NULL COMMENT '首次发放时间',
  updated_at DATETIME NOT NULL COMMENT '最近同步时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_order_reward (tenant_id, order_no),
  KEY idx_member_order_reward_user (tenant_id, user_id)
) COMMENT='会员餐饮订单经验积分发放与退款撤回记录';
