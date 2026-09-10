-- 套餐运营展示字段与基础功能展示目录（MySQL 8，可重复执行）
-- 依赖：22_saas_entitlements.sql；旧环境执行本脚本后再部署新版 user-service / 管理端。
SET NAMES utf8mb4;
USE liteshop_user;

DROP PROCEDURE IF EXISTS migrate_saas_plan_catalog_ui_20260908;
DELIMITER $$
CREATE PROCEDURE migrate_saas_plan_catalog_ui_20260908()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan' AND column_name='target_customer') THEN
    ALTER TABLE saas_plan ADD COLUMN target_customer VARCHAR(200) NULL COMMENT '套餐适用客户' AFTER plan_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan' AND column_name='core_benefits') THEN
    ALTER TABLE saas_plan ADD COLUMN core_benefits VARCHAR(1000) NULL COMMENT '换行分隔的核心权益展示文案' AFTER target_customer;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='saas_plan' AND column_name='recommended') THEN
    ALTER TABLE saas_plan ADD COLUMN recommended TINYINT NOT NULL DEFAULT 0 COMMENT '运营推荐套餐' AFTER description;
  END IF;
END$$
DELIMITER ;
CALL migrate_saas_plan_catalog_ui_20260908();
DROP PROCEDURE IF EXISTS migrate_saas_plan_catalog_ui_20260908;

CREATE TABLE IF NOT EXISTS saas_display_feature (
  id BIGINT NOT NULL,
  module_code VARCHAR(100) NOT NULL,
  module_name VARCHAR(100) NOT NULL,
  feature_code VARCHAR(100) NOT NULL,
  feature_name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_saas_display_feature_code (feature_code),
  KEY idx_saas_display_feature_module (module_code,status,sort_order)
) ENGINE=InnoDB COMMENT='套餐对比页基础功能展示目录，不参与运行时鉴权';

INSERT INTO saas_display_feature
  (id,module_code,module_name,feature_code,feature_name,description,sort_order,status,create_time,update_time)
VALUES
  (881700000000000001,'store','门店与基础管理','store_info','门店信息管理','门店基本信息与营业设置',10,1,NOW(),NOW()),
  (881700000000000002,'store','门店与基础管理','employee_account','员工账号','员工、店长和管理员账号',20,1,NOW(),NOW()),
  (881700000000000003,'store','门店与基础管理','table_room','桌台/包间管理','桌台、包间及区域管理',30,1,NOW(),NOW()),
  (881700000000000004,'store','门店与基础管理','business_hours','营业时间设置','营业时段与节假日设置',40,1,NOW(),NOW()),
  (881700000000000005,'ordering','点餐与菜品','qr_ordering','扫码点餐','顾客扫码自助点餐',10,1,NOW(),NOW()),
  (881700000000000006,'ordering','点餐与菜品','dish_management','菜品管理','菜品、分类、规格及库存管理',20,1,NOW(),NOW()),
  (881700000000000007,'ordering','点餐与菜品','table_ordering','桌台点餐','按桌台开台、加菜和结账',30,1,NOW(),NOW()),
  (881700000000000008,'ordering','点餐与菜品','delivery_orders','外卖接单','外卖订单统一接单与处理',40,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE
  module_code=VALUES(module_code),module_name=VALUES(module_name),feature_name=VALUES(feature_name),
  description=VALUES(description),sort_order=VALUES(sort_order),status=VALUES(status),update_time=NOW();

-- 为已有套餐补齐运营展示信息，不覆盖已经维护过的内容。
UPDATE saas_plan SET target_customer=COALESCE(target_customer,'适合单店与小型团队'),
  core_benefits=COALESCE(core_benefits,'全部基础经营功能\n3 家门店') WHERE plan_code='basic';
UPDATE saas_plan SET target_customer=COALESCE(target_customer,'适合成长型餐饮商户'),
  core_benefits=COALESCE(core_benefits,'全部基础经营功能\n20 家门店\n支持额外门店加购') WHERE plan_code='pro';
UPDATE saas_plan SET target_customer=COALESCE(target_customer,'适合多门店连锁品牌'),
  core_benefits=COALESCE(core_benefits,'全部基础经营功能\n不限门店\n支持专属服务') WHERE plan_code='chain';
