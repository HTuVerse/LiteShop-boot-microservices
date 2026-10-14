-- AI 店长 V2：拆分经营顾问，并补充生成失败信息。
SET NAMES utf8mb4;
USE liteshop_user;

ALTER TABLE saas_ai_generation
  ADD COLUMN failure_code VARCHAR(64) NULL COMMENT '可安全展示的失败分类' AFTER status,
  ADD COLUMN failure_message VARCHAR(500) NULL COMMENT '脱敏后的失败原因' AFTER failure_code,
  ADD COLUMN retryable TINYINT(1) NULL COMMENT '是否允许用户重新发起：0否 1是' AFTER failure_message,
  ADD COLUMN quota_charged TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否对该记录单独扣减一次额度：0否 1是' AFTER retryable;

SET @operations_menu := (SELECT id FROM sys_permission WHERE permission_code='operations:menu' LIMIT 1);
SET @ai_manager := (SELECT id FROM sys_permission WHERE permission_code='operations:assistant' LIMIT 1);
SET @ai_menu_parent := COALESCE(
  (SELECT id FROM sys_permission WHERE permission_code='ai:manager:menu' LIMIT 1),
  @operations_menu
);

INSERT INTO sys_permission
  (id,permission_name,permission_code,parent_id,permission_type,component_address,
   router_address,sort,status,icon,is_hide,is_iframe,keep_alive,create_time,update_time)
SELECT 77,'经营顾问','operations:assistant:advisor',@ai_menu_parent,1,
       'operations/advisor/index','/operations/advisor',20,1,'ri:chat-ai-line',0,0,0,NOW(),NOW()
WHERE @ai_menu_parent IS NOT NULL
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),component_address=VALUES(component_address),
  router_address=VALUES(router_address),parent_id=VALUES(parent_id),sort=VALUES(sort),status=1,update_time=NOW();

-- 继承原 AI 店长菜单的角色授权，避免升级后普通管理角色丢失新入口。
INSERT INTO sys_role_permission (tenant_id,role_id,permission_id)
SELECT source.tenant_id,source.role_id,target.id
FROM sys_role_permission source
JOIN sys_permission target ON target.permission_code='operations:assistant:advisor'
WHERE source.permission_id=@ai_manager
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id=source.role_id AND existing.permission_id=target.id
  );

