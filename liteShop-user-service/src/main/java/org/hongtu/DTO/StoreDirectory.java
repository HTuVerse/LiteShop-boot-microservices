package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户服务保存的门店目录投影，仅用于本地授权校验与名称展示。 */
@Data
@TableName("sys_store_directory")
public class StoreDirectory {
    private Long tenantId;
    private Long storeId;
    private String storeName;
    /** 是否可用于新的门店授权。 */
    private Boolean active;
    private Long syncVersion;
}
