USE `liteshop_restaurant`;

-- 订单或订单项发生变化时，只记录受影响的“租户-门店-营业日”。
-- 队列表与业务写入处于同一个数据库事务，服务重启也不会丢失刷新请求。
CREATE TABLE IF NOT EXISTS `analysis_refresh_queue` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `biz_date` date NOT NULL COMMENT '需要重算的支付归属自然日',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `requested_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次请求刷新时间',
  PRIMARY KEY (`tenant_id`, `store_id`, `biz_date`),
  INDEX `idx_analysis_refresh_requested` (`requested_at`, `tenant_id`, `store_id`, `biz_date`)
) ENGINE=InnoDB COMMENT='经营分析门店营业日刷新队列';

DROP TRIGGER IF EXISTS `trg_order_analysis_ai`;
DROP TRIGGER IF EXISTS `trg_order_analysis_au`;
DROP TRIGGER IF EXISTS `trg_order_analysis_ad`;
DROP TRIGGER IF EXISTS `trg_order_item_analysis_ai`;
DROP TRIGGER IF EXISTS `trg_order_item_analysis_au`;
DROP TRIGGER IF EXISTS `trg_order_item_analysis_ad`;

DELIMITER $$

CREATE TRIGGER `trg_order_analysis_ai`
AFTER INSERT ON `restaurant_order`
FOR EACH ROW
BEGIN
  IF NEW.payment_time IS NOT NULL THEN
    INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
    VALUES (NEW.tenant_id, DATE(NEW.payment_time), NEW.store_id, CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
  END IF;
END$$

CREATE TRIGGER `trg_order_analysis_au`
AFTER UPDATE ON `restaurant_order`
FOR EACH ROW
BEGIN
  IF OLD.payment_time IS NOT NULL THEN
    INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
    VALUES (OLD.tenant_id, DATE(OLD.payment_time), OLD.store_id, CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
  END IF;
  IF NEW.payment_time IS NOT NULL THEN
    INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
    VALUES (NEW.tenant_id, DATE(NEW.payment_time), NEW.store_id, CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
  END IF;
END$$

CREATE TRIGGER `trg_order_analysis_ad`
AFTER DELETE ON `restaurant_order`
FOR EACH ROW
BEGIN
  IF OLD.payment_time IS NOT NULL THEN
    INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
    VALUES (OLD.tenant_id, DATE(OLD.payment_time), OLD.store_id, CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
  END IF;
END$$

CREATE TRIGGER `trg_order_item_analysis_ai`
AFTER INSERT ON `restaurant_order_item`
FOR EACH ROW
BEGIN
  INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
  SELECT ro.tenant_id, DATE(ro.payment_time), ro.store_id, CURRENT_TIMESTAMP(3)
  FROM restaurant_order ro
  WHERE ro.tenant_id = NEW.tenant_id AND ro.id = NEW.order_id AND ro.payment_time IS NOT NULL
  ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
END$$

CREATE TRIGGER `trg_order_item_analysis_au`
AFTER UPDATE ON `restaurant_order_item`
FOR EACH ROW
BEGIN
  INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
  SELECT ro.tenant_id, DATE(ro.payment_time), ro.store_id, CURRENT_TIMESTAMP(3)
  FROM restaurant_order ro
  WHERE ro.tenant_id = OLD.tenant_id AND ro.id = OLD.order_id AND ro.payment_time IS NOT NULL
  ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);

  INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
  SELECT ro.tenant_id, DATE(ro.payment_time), ro.store_id, CURRENT_TIMESTAMP(3)
  FROM restaurant_order ro
  WHERE ro.tenant_id = NEW.tenant_id AND ro.id = NEW.order_id AND ro.payment_time IS NOT NULL
  ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
END$$

CREATE TRIGGER `trg_order_item_analysis_ad`
AFTER DELETE ON `restaurant_order_item`
FOR EACH ROW
BEGIN
  INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
  SELECT ro.tenant_id, DATE(ro.payment_time), ro.store_id, CURRENT_TIMESTAMP(3)
  FROM restaurant_order ro
  WHERE ro.tenant_id = OLD.tenant_id AND ro.id = OLD.order_id AND ro.payment_time IS NOT NULL
  ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
END$$

DELIMITER ;

-- 升级切换时主动刷新最近两天，承接旧定时任务最后一次运行后的数据变化。
INSERT INTO analysis_refresh_queue (tenant_id, biz_date, store_id, requested_at)
SELECT DISTINCT tenant_id, DATE(payment_time), store_id, CURRENT_TIMESTAMP(3)
FROM restaurant_order
WHERE payment_time >= DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE requested_at = CURRENT_TIMESTAMP(3);
