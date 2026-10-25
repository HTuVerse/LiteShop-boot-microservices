package org.hongtu.api.dto;

import lombok.Data;

/** 餐厅服务单向发布的门店基础资料，供用户服务本地校验授权范围。 */
@Data
public class StoreDirectoryDTO {
    private Long storeId;
    private String storeName;
    /** 是否仍可分配给管理账号。 */
    private boolean active;
    /** 同一门店变更的顺序号；存量初始化使用零。 */
    private long syncVersion;
}
