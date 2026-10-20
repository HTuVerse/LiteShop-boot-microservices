-- 已有环境升级：优惠券回收状态、审计记录和后台操作权限。
USE liteshop_coupon;

ALTER TABLE coupon
  ADD COLUMN recall_time datetime NULL COMMENT '模板批量回收时间；非空时永久停止发放' AFTER status;

ALTER TABLE user_coupon
  MODIFY COLUMN status tinyint NOT NULL DEFAULT 0
  COMMENT '状态(0:未生效,1:未使用,2:已使用,3:已过期,4:已回收)';

CREATE TABLE IF NOT EXISTS coupon_revoke_record (
  id bigint NOT NULL COMMENT '回收记录ID',
  user_coupon_id bigint NOT NULL COMMENT '用户券ID',
  coupon_id bigint NOT NULL COMMENT '券模板ID',
  user_id bigint NOT NULL COMMENT '持券用户ID',
  operator_id bigint NULL COMMENT '操作管理员ID；系统取消订单回收时为空',
  reason varchar(200) NOT NULL COMMENT '回收原因',
  batch_id varchar(36) NULL COMMENT '模板批量回收批次号',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回收时间',
  tenant_id bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_user_coupon (tenant_id, user_coupon_id),
  KEY idx_tenant_coupon_time (tenant_id, coupon_id, create_time)
) ENGINE=InnoDB COMMENT='用户券回收审计记录';

USE liteshop_user;
-- 已拥有优惠券管理菜单的后台角色获得回收按钮权限。
INSERT INTO sys_permission (id, permission_name, permission_code, parent_id, permission_type,
  component_address, router_address, sort, status, is_hide, is_iframe, keep_alive)
SELECT 900118, '回收优惠券', 'marketing:coupon:revoke', p.id, 2,
  NULL, NULL, 115, 1, 0, 0, 0
FROM sys_permission p
WHERE p.permission_code = 'marketing:coupon'
  AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code = 'marketing:coupon:revoke');

CREATE TEMPORARY TABLE tmp_coupon_revoke_grants AS
SELECT DISTINCT rp.tenant_id, rp.role_id, child.id AS permission_id
FROM sys_role_permission rp
JOIN sys_permission parent ON parent.id = rp.permission_id
JOIN sys_permission child ON child.parent_id = parent.id
JOIN sys_role r ON r.id = rp.role_id
WHERE parent.permission_code = 'marketing:coupon'
  AND child.permission_code = 'marketing:coupon:revoke'
  AND r.role_code <> 'USER';
INSERT INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT g.tenant_id, g.role_id, g.permission_id
FROM tmp_coupon_revoke_grants g
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp
  WHERE rp.tenant_id <=> g.tenant_id AND rp.role_id = g.role_id AND rp.permission_id = g.permission_id);
DROP TEMPORARY TABLE tmp_coupon_revoke_grants;
