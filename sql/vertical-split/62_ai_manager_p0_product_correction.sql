-- AI 店长 P0 产品纠偏：收敛菜单并隐藏独立经营问答入口。
SET NAMES utf8mb4;
USE liteshop_user;

UPDATE sys_permission
SET permission_name='今日经营', sort=10, icon='ri:sparkling-line', is_hide=0, update_time=NOW()
WHERE permission_code='operations:assistant' AND permission_type=1;

-- 经营问答保留兼容路由和权限，由“今日经营”页进入，不再单独占用菜单。
UPDATE sys_permission
SET permission_name='经营问答', sort=15, icon='ri:chat-ai-line', is_hide=1, update_time=NOW()
WHERE permission_code='operations:assistant:advisor' AND permission_type=1;

