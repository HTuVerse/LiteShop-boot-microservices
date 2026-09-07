-- MySQL 8.0。先完成 12_member_level_schemes.sql，再执行本文件，最后重启新版 user-service。
-- 新增平台模板的租户分配关系。不会切换、删除或重算租户已复制的等级方案。
SET NAMES utf8mb4;
USE liteshop_user;
CREATE TABLE IF NOT EXISTS user_level_template_tenant (
  scheme_id BIGINT NOT NULL COMMENT '平台等级体系 ID，user_level_scheme.tenant_id 必须为 NULL',
  tenant_id BIGINT NOT NULL COMMENT '被授权选择该体系的租户 ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (scheme_id, tenant_id),
  KEY idx_level_template_tenant (tenant_id, scheme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台等级体系租户分配';

-- 平台当前默认体系始终供所有租户使用，无需逐个插入分配关系。
-- 其他平台体系只对已分配租户可见。已有租户副本保持独立，不会被撤销分配删除。
-- 示例：将零售示例体系分配给两个已存在的测试租户；用真实租户 ID 替换 NULL 后可单独执行。
SET @assigned_template_id = 202609040102;
SET @assigned_tenant_a = NULL;
SET @assigned_tenant_b = NULL;
INSERT INTO user_level_template_tenant(scheme_id,tenant_id)
SELECT s.id,t.id FROM user_level_scheme s CROSS JOIN sys_tenant t
WHERE s.id=@assigned_template_id AND s.tenant_id IS NULL AND s.status=1
  AND t.id IN (@assigned_tenant_a,@assigned_tenant_b)
  AND NOT EXISTS(SELECT 1 FROM user_level_template_tenant g WHERE g.scheme_id=s.id AND g.tenant_id=t.id);

SELECT s.id AS scheme_id,s.name AS scheme_name,t.id AS tenant_id,t.tenant_name
FROM user_level_template_tenant g
JOIN user_level_scheme s ON s.id=g.scheme_id
JOIN sys_tenant t ON t.id=g.tenant_id
ORDER BY s.id,t.id;
