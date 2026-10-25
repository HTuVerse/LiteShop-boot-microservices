package org.hongtu.restaurant.DTO;

import lombok.Data;

/** 存量门店目录初始化时使用的跨租户游标行。 */
@Data
public class StoreDirectoryRow {
    private Long id;
    private Long tenantId;
    private String storeName;
}
