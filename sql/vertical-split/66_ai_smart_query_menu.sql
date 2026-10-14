-- AI 店长智能问数：恢复独立子菜单，并沿用经营顾问的权限与兼容路由。
SET NAMES utf8mb4;
USE liteshop_user;

SET @ai_root := (SELECT id FROM sys_permission WHERE permission_code='ai:manager:menu' LIMIT 1);

UPDATE sys_permission
SET permission_name='AI 智能问数',
    parent_id=COALESCE(@ai_root,parent_id),
    component_address='operations/advisor/index',
    router_address='/operations/advisor',
    sort=15,
    status=1,
    icon='ri:chat-ai-line',
    is_hide=0,
    update_time=NOW()
WHERE permission_code='operations:assistant:advisor' AND permission_type=1;

-- 已有智能问数权限的角色补齐 AI 店长目录权限，不扩大其他功能权限。
INSERT INTO sys_role_permission (tenant_id,role_id,permission_id)
SELECT DISTINCT source.tenant_id,source.role_id,@ai_root
FROM sys_role_permission source
JOIN sys_permission child ON child.id=source.permission_id
WHERE @ai_root IS NOT NULL
  AND child.permission_code='operations:assistant:advisor'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id=source.role_id AND existing.permission_id=@ai_root);
