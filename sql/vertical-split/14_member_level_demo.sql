-- MySQL 8.0；先执行后端 sql/vertical-split/12_member_level_schemes.sql。
-- 开发环境模拟数据，暂停等级配置写入后手动执行；不创建账号、不修改经验/积分/余额。
-- 可重跑：不覆盖已有等级内容、平台默认选择或租户启用体系。
SET NAMES utf8mb4;
USE liteshop_user;

-- 可选：填写已存在的测试租户 ID，为没有启用体系的租户复制示例体系。
-- NULL 表示只生成平台模板。不要填写商户编码或用户 ID。
SET @demo_tenant_id = NULL;
SET @demo_template_id = 202609040101; -- 202609040101 餐饮；202609040102 零售

DROP PROCEDURE IF EXISTS seed_member_levels_20260904;
DELIMITER $$
CREATE PROCEDURE seed_member_levels_20260904()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_scheme BIGINT;
  DECLARE v_level BIGINT;
  DECLARE v_count BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  IF EXISTS (SELECT 1 FROM user_level_scheme WHERE id IN (202609040101,202609040102)
      AND (tenant_id IS NOT NULL OR name NOT IN ('餐饮会员标准体系（示例）','零售会员成长体系（示例）'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='示例方案ID已被其他数据占用，请调整示例ID后执行';
  END IF;
  INSERT INTO user_level_scheme(id,tenant_id,name,description,is_active,is_default,status)
    SELECT 202609040101,NULL,'餐饮会员标准体系（示例）','5级餐饮示例；权益为展示文案，实际优惠需另行配置',0,0,1
    WHERE NOT EXISTS(SELECT 1 FROM user_level_scheme WHERE id=202609040101);
  INSERT INTO user_level_scheme(id,tenant_id,name,description,is_active,is_default,status)
    SELECT 202609040102,NULL,'零售会员成长体系（示例）','5级零售示例；经验门槛与餐饮体系独立',0,0,1
    WHERE NOT EXISTS(SELECT 1 FROM user_level_scheme WHERE id=202609040102);

  DROP TEMPORARY TABLE IF EXISTS tmp_demo_levels;
  CREATE TEMPORARY TABLE tmp_demo_levels(
    scheme_id BIGINT, level_code INT, level_name VARCHAR(50), required_exp INT,
    benefits VARCHAR(1000), PRIMARY KEY(scheme_id,level_code)
  );
  INSERT INTO tmp_demo_levels VALUES
    (202609040101,0,'普通会员',0,'会员价、消费累计成长值'),
    (202609040101,1,'银卡会员',500,'生日关怀、会员活动邀请'),
    (202609040101,2,'金卡会员',2000,'专属优惠券、生日礼遇'),
    (202609040101,3,'铂金会员',5000,'新品试吃、专属客服'),
    (202609040101,4,'钻石会员',10000,'高阶会员活动、周年礼遇'),
    (202609040102,0,'新客会员',0,'消费累计成长值'),
    (202609040102,1,'成长会员',1000,'会员日活动'),
    (202609040102,2,'优享会员',3000,'专属优惠券'),
    (202609040102,3,'尊享会员',8000,'生日礼遇、优先客服'),
    (202609040102,4,'黑金会员',20000,'专属活动、周年礼遇');
  SELECT COALESCE(MAX(id),0) INTO v_level FROM user_level;
  SELECT COUNT(*) INTO v_count FROM tmp_demo_levels d
    WHERE NOT EXISTS(SELECT 1 FROM user_level l WHERE l.scheme_id=d.scheme_id AND l.level_code=d.level_code);
  IF CAST(v_level AS DECIMAL(20,0))+v_count > 9223372036854775807 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='等级ID空间不足';
  END IF;
  INSERT INTO user_level(id,tenant_id,scheme_id,level_code,level_name,required_exp,benefits,description,icon,status,sort)
    SELECT v_level+ROW_NUMBER() OVER(ORDER BY d.scheme_id,d.level_code),NULL,d.scheme_id,
      d.level_code,d.level_name,d.required_exp,d.benefits,'模拟数据，可在等级管理中修改','ri:vip-crown-line',1,d.level_code
    FROM tmp_demo_levels d
    WHERE NOT EXISTS(SELECT 1 FROM user_level l WHERE l.scheme_id=d.scheme_id AND l.level_code=d.level_code);

  -- 首次无默认体系时设餐饮为默认；保留原有默认体系（包括旧迁移产生的标准体系）。
  IF NOT EXISTS(SELECT 1 FROM user_level_scheme WHERE tenant_id IS NULL AND is_default=1) THEN
    UPDATE user_level_scheme SET is_default=1,update_time=NOW() WHERE id=202609040101 AND status=1;
  END IF;

  IF @demo_tenant_id IS NOT NULL THEN
    IF NOT EXISTS(SELECT 1 FROM sys_tenant WHERE id=@demo_tenant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标测试租户不存在，请通过商户管理创建租户';
    END IF;
    IF @demo_template_id IS NULL OR @demo_template_id NOT IN (202609040101,202609040102) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='请选择餐饮或零售示例模板ID';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM user_level_scheme WHERE tenant_id=@demo_tenant_id AND is_active=1) THEN
      IF NOT EXISTS(SELECT 1 FROM user_level WHERE scheme_id=@demo_template_id AND tenant_id IS NULL AND status=1 AND required_exp=0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='示例模板缺少启用的零经验基础等级';
      END IF;
      SELECT COALESCE(MAX(id),0)+1 INTO v_scheme FROM user_level_scheme;
      INSERT INTO user_level_scheme(id,tenant_id,name,description,source_template_id,is_active,is_default,status)
        VALUES(v_scheme,@demo_tenant_id,'测试租户会员等级','从示例模板复制，租户独立维护',@demo_template_id,1,0,1);
      SELECT COALESCE(MAX(id),0) INTO v_level FROM user_level;
      SELECT COUNT(*) INTO v_count FROM user_level WHERE scheme_id=@demo_template_id AND tenant_id IS NULL;
      IF CAST(v_level AS DECIMAL(20,0))+v_count > 9223372036854775807 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='等级ID空间不足';
      END IF;
      INSERT INTO user_level(id,tenant_id,scheme_id,level_code,level_name,required_exp,benefits,description,icon,status,sort)
        SELECT v_level+ROW_NUMBER() OVER(ORDER BY level_code),@demo_tenant_id,v_scheme,
          level_code,level_name,required_exp,benefits,description,icon,status,sort
        FROM user_level WHERE scheme_id=@demo_template_id AND tenant_id IS NULL;
      UPDATE sys_user u SET u.level=COALESCE((
        SELECT l.level_code FROM user_level l WHERE l.scheme_id=v_scheme AND l.tenant_id=@demo_tenant_id
          AND l.status=1 AND l.required_exp<=COALESCE(u.exp,0) ORDER BY l.required_exp DESC LIMIT 1
      ),0) WHERE u.tenant_id=@demo_tenant_id;
    END IF;
  END IF;
  DROP TEMPORARY TABLE tmp_demo_levels;
  COMMIT;
END$$
DELIMITER ;
CALL seed_member_levels_20260904();
DROP PROCEDURE IF EXISTS seed_member_levels_20260904;

SELECT s.id,s.tenant_id,s.name,s.is_active,s.is_default,COUNT(l.id) AS level_count
FROM user_level_scheme s LEFT JOIN user_level l ON l.scheme_id=s.id
GROUP BY s.id ORDER BY s.tenant_id,s.id;
-- 以下结果展示缺少启用体系、运行时将使用平台默认的租户。
SELECT t.id,t.tenant_name FROM sys_tenant t
WHERE NOT EXISTS(SELECT 1 FROM user_level_scheme s WHERE s.tenant_id=t.id AND s.is_active=1 AND s.status=1);
