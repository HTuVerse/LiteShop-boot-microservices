package org.hongtu.restaurant.DTO;

import lombok.Data;

/** 门店库按租户汇总的已提交门店数量。 */
@Data
public class TenantStoreCount {
    private Long tenantId;
    private Long storeCount;
}
