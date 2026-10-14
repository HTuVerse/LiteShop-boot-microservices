USE `liteshop_user`;

-- 将「今日经营」入口升级为「AI 经营诊断」动态诊断页，沿用原权限与角色授权，仅改名称/组件/路由。
-- 页面细分权限（operations:ai:diagnosis 等）已由 59_ai_manager_p1_workspace.sql 建立，无需重建。
UPDATE `sys_permission`
SET `permission_name`   = 'AI 经营诊断',
    `component_address` = 'operations/aiAnalysis/index',
    `router_address`    = '/operations/ai-analysis',
    `icon`              = 'ri:robot-2-line',
    `is_hide`           = 0,
    `update_time`       = NOW()
WHERE `permission_code` = 'operations:assistant'
  AND `permission_type` = 1;
