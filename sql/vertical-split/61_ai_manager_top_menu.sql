-- AI 店长独立顶级菜单：经营洞察与经营顾问统一归入 AI 店长。
SET NAMES utf8mb4;
USE liteshop_user;

SET @ai_root := (SELECT id FROM sys_permission WHERE permission_code='ai:manager:menu' LIMIT 1);
SET @next_permission_id := (SELECT GREATEST(COALESCE(MAX(id), 0) + 1, 86) FROM sys_permission);
SET @ai_root := COALESCE(@ai_root, @next_permission_id);

INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,component_address,
   router_address,sort,status,icon,is_hide,is_iframe,keep_alive,create_time,update_time)
SELECT @ai_root,'AI店长','ai:manager:menu',0,1,'Layout','/ai-manager',55,1,
       'ri:robot-line',0,0,0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='ai:manager:menu');

SET @ai_root := (SELECT id FROM sys_permission WHERE permission_code='ai:manager:menu' LIMIT 1);

UPDATE sys_permission
SET permission_name='AI店长', parent_id=0, permission_type=1,
    component_address='Layout', router_address='/ai-manager', sort=55,
    status=1, icon='ri:robot-line', is_hide=0, update_time=NOW()
WHERE id=@ai_root;

UPDATE sys_permission
SET permission_name='经营洞察', parent_id=@ai_root, sort=10,
    icon='ri:sparkling-line', update_time=NOW()
WHERE permission_code='operations:assistant' AND permission_type=1;

UPDATE sys_permission
SET parent_id=@ai_root, sort=20, update_time=NOW()
WHERE permission_code='operations:assistant:advisor' AND permission_type=1;

-- 已拥有任一 AI 页面权限的角色自动获得顶级目录权限，不扩大具体功能权限。
INSERT INTO sys_role_permission (tenant_id,role_id,permission_id)
SELECT DISTINCT source.tenant_id,source.role_id,@ai_root
FROM sys_role_permission source
JOIN sys_permission child ON child.id=source.permission_id
WHERE child.permission_code IN
  ('operations:assistant','operations:assistant:advisor')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id=source.role_id AND existing.permission_id=@ai_root);
