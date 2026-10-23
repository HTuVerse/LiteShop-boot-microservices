-- 各业务库的审计事件投递箱。必须在启用对应服务的审计采集前执行。
-- 投递成功后只清理已确认投递的箱内事件；正式审计日志不从业务库清理。
USE `liteshop_user`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` (
  `event_id` varchar(36) NOT NULL COMMENT '审计事件全局幂等ID',
  `payload` json NOT NULL COMMENT '脱敏后的审计事件内容',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件入箱时间',
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次投递时间',
  `attempts` int NOT NULL DEFAULT 0 COMMENT '已投递次数',
  `delivered_at` datetime(3) NULL COMMENT '消息代理确认接收的时间',
  PRIMARY KEY (`event_id`),
  KEY `idx_audit_outbox_pending` (`delivered_at`,`next_attempt_at`,`created_at`)
) ENGINE=InnoDB COMMENT='系统审计事件持久化投递箱';

USE `liteshop_restaurant`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` LIKE `liteshop_user`.`sys_audit_outbox`;
USE `liteshop_config`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` LIKE `liteshop_user`.`sys_audit_outbox`;
USE `liteshop_coupon`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` LIKE `liteshop_user`.`sys_audit_outbox`;
USE `liteshop_lottery`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` LIKE `liteshop_user`.`sys_audit_outbox`;
USE `liteshop_mall`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` LIKE `liteshop_user`.`sys_audit_outbox`;
USE `liteshop_file`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` LIKE `liteshop_user`.`sys_audit_outbox`;
USE `liteshop_message`;
CREATE TABLE IF NOT EXISTS `sys_audit_outbox` LIKE `liteshop_user`.`sys_audit_outbox`;
