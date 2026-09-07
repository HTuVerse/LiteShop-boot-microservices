-- ============================================================
-- liteshop_file  —— 文件服务库（liteShop-file-service）
-- 变更说明（相对 hongtu 单库）：
--   1. 主键改雪花 ID：原 int UNSIGNED 升为 bigint（对应 Java 实体 Integer -> Long）
--   2. 末尾追加 created_at（原表没有时间字段）与 tenant_id（多租户预留）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `liteshop_file` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `liteshop_file`;

-- ----------------------------
-- 文件上传表
-- ----------------------------
DROP TABLE IF EXISTS `file_upload`;
CREATE TABLE `file_upload` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `original_file_name` varchar(255) NULL DEFAULT NULL COMMENT '文件原始名称',
  `size` bigint NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `content_type` varchar(128) NULL DEFAULT NULL COMMENT '文件类型',
  `url` varchar(500) NULL DEFAULT NULL COMMENT '文件访问URL',
  `storage_type` varchar(20) NULL DEFAULT NULL COMMENT '存储类型（local/oss）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID（多租户预留）',
  PRIMARY KEY (`id`),
  INDEX `idx_created_at`(`created_at`)
) ENGINE = InnoDB COMMENT = '文件上传表';
