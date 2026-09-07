-- ============================================
-- 系统配置模块数据库脚本
-- 作者: Ht
-- 日期: 2025-02-03
-- ============================================

-- -------------------------------------------
-- 1. 轮播图配置表
-- -------------------------------------------
DROP TABLE IF EXISTS `sys_banner`;
CREATE TABLE `sys_banner` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(100) DEFAULT NULL COMMENT '标题',
    `image_url` VARCHAR(500) NOT NULL COMMENT '图片地址',
    `link_url` VARCHAR(500) DEFAULT NULL COMMENT '跳转链接',
    `link_type` TINYINT DEFAULT 3 COMMENT '跳转类型：1-小程序页面 2-H5链接 3-不跳转',
    `position` TINYINT NOT NULL COMMENT '轮播图位置：1-首页轮播图 2-点餐页轮播图 3-个人中心轮播图 4-活动页轮播图',
    `sort_order` INT DEFAULT 0 COMMENT '排序值，数字越小越靠前',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `start_time` DATETIME DEFAULT NULL COMMENT '开始时间（用于定时展示）',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间（用于定时展示）',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_position_status` (`position`, `status`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮播图配置表';

-- -------------------------------------------
-- 2. 系统配置表（键值对形式，灵活扩展）
-- -------------------------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键（唯一）',
    `config_value` TEXT COMMENT '配置值（可存JSON）',
    `config_type` TINYINT DEFAULT 2 COMMENT '配置类型：1-图片 2-文本 3-JSON 4-颜色 5-数字',
    `config_group` TINYINT DEFAULT 5 COMMENT '配置分组：1-默认图片配置 2-应用信息配置 3-主题配置 4-联系方式配置 5-其他配置',
    `config_name` VARCHAR(100) DEFAULT NULL COMMENT '配置名称（中文说明）',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '配置描述',
    `editable` TINYINT DEFAULT 1 COMMENT '是否允许修改：0-否 1-是',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`),
    KEY `idx_config_group` (`config_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';


-- ============================================
-- 初始化数据
-- ============================================

-- -------------------------------------------
-- 首页轮播图初始数据
-- -------------------------------------------
INSERT INTO `sys_banner` (`title`, `image_url`, `link_url`, `link_type`, `position`, `sort_order`, `status`, `remark`) VALUES
('新品推荐', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800', NULL, 3, 1, 1, 1, '首页轮播图-美食图片1'),
('限时特惠', 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800', NULL, 3, 1, 2, 1, '首页轮播图-美食图片2'),
('会员专享', 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?w=800', NULL, 3, 1, 3, 1, '首页轮播图-美食图片3'),
('热门推荐', 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=800', NULL, 3, 1, 4, 1, '首页轮播图-披萨图片'),
('精选好物', 'https://images.unsplash.com/photo-1476224203421-9ac39bcb3327?w=800', NULL, 3, 1, 5, 1, '首页轮播图-精选美食');

-- -------------------------------------------
-- 点餐页轮播图初始数据
-- -------------------------------------------
INSERT INTO `sys_banner` (`title`, `image_url`, `link_url`, `link_type`, `position`, `sort_order`, `status`, `remark`) VALUES
('新品上市', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800', NULL, 3, 2, 1, 1, '点餐页轮播图-新品'),
('限时特惠', 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800', NULL, 3, 2, 2, 1, '点餐页轮播图-特惠'),
('会员专享', 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?w=800', NULL, 3, 2, 3, 1, '点餐页轮播图-会员');

-- -------------------------------------------
-- 系统配置初始数据
-- -------------------------------------------

-- 应用信息配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_group`, `config_name`, `description`, `editable`) VALUES
('app_name', 'LiteShop点餐', 2, 2, '应用名称', '小程序显示的应用名称', 1),
('app_logo', 'https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?w=200', 1, 2, '应用Logo', '小程序Logo图片地址', 1),
('app_desc', '便捷点餐，美味即达', 2, 2, '应用描述', '小程序描述文字', 1);

-- 默认图片配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_group`, `config_name`, `description`, `editable`) VALUES
('home_background_image', 'https://images.unsplash.com/photo-1495195134817-aeb325a55b65?w=800', 1, 1, '首页背景图片', '小程序首页背景图片地址', 1),
('default_dish_image', 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400', 1, 1, '默认菜品图片', '菜品没有图片时显示的默认图片', 1),
('default_avatar', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200', 1, 1, '默认头像', '用户没有头像时显示的默认头像', 1),
('default_empty_image', 'https://images.unsplash.com/photo-1584824486509-112e4181ff6b?w=400', 1, 1, '默认空图片', '无数据时显示的占位图片', 1),
('default_order_image', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400', 1, 1, '默认订单图片', '订单没有图片时显示的默认图片', 1);

-- 主题配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_group`, `config_name`, `description`, `editable`) VALUES
('theme_color', '#FF6B35', 4, 3, '主题色', '小程序主题颜色', 1),
('theme_color_light', '#FFE4D4', 4, 3, '主题浅色', '主题浅色用于背景等', 1),
('theme_color_dark', '#E55A2B', 4, 3, '主题深色', '主题深色用于强调等', 1);

-- 联系方式配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_group`, `config_name`, `description`, `editable`) VALUES
('service_phone', '400-123-4567', 2, 4, '客服电话', '客服联系电话', 1),
('service_wechat', 'LiteShop_Service', 2, 4, '客服微信', '客服微信号', 1),
('company_address', '北京市朝阳区xxx街道xxx号', 2, 4, '公司地址', '公司联系地址', 1);

-- 其他配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_group`, `config_name`, `description`, `editable`) VALUES
('announcement', '欢迎使用LiteShop点餐系统！', 2, 5, '公告内容', '首页公告内容', 1),
('announcement_enabled', '1', 5, 5, '公告开关', '是否显示公告：0-关闭 1-开启', 1),
('share_title', '我在LiteShop发现了好吃的，快来看看！', 2, 5, '分享标题', '小程序分享标题', 1),
('share_image', 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=500', 1, 5, '分享图片', '小程序分享图片', 1);


-- ============================================
-- 配置键说明（供开发参考）
-- ============================================
-- 
-- 【应用信息配置 - config_group = 2】
-- app_name              : 应用名称
-- app_logo              : 应用Logo
-- app_desc              : 应用描述
--
-- 【默认图片配置 - config_group = 1】
-- home_background_image : 首页背景图片
-- default_dish_image    : 默认菜品图片
-- default_avatar        : 默认头像
-- default_empty_image   : 默认空图片（无数据占位）
-- default_order_image   : 默认订单图片
--
-- 【主题配置 - config_group = 3】
-- theme_color           : 主题色
-- theme_color_light     : 主题浅色
-- theme_color_dark      : 主题深色
--
-- 【联系方式配置 - config_group = 4】
-- service_phone         : 客服电话
-- service_wechat        : 客服微信
-- company_address       : 公司地址
--
-- 【其他配置 - config_group = 5】
-- announcement          : 公告内容
-- announcement_enabled  : 公告开关
-- share_title           : 分享标题
-- share_image           : 分享图片
-- ============================================
