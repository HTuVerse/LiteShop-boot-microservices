-- 用户服务只读门店目录，消除用户服务到餐厅服务的同步请求。
USE liteshop_user;

CREATE TABLE IF NOT EXISTS sys_store_directory (
    tenant_id BIGINT NOT NULL COMMENT '门店所属租户ID',
    store_id BIGINT NOT NULL COMMENT '餐厅服务门店ID',
    store_name VARCHAR(100) NOT NULL COMMENT '门店名称快照',
    active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可用于授权：0否，1是',
    sync_version BIGINT NOT NULL DEFAULT 0 COMMENT '门店变更顺序号，零表示存量初始化',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '目录最后同步时间',
    PRIMARY KEY (tenant_id, store_id),
    KEY idx_store_directory_active (tenant_id, active, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户服务本地门店目录';
