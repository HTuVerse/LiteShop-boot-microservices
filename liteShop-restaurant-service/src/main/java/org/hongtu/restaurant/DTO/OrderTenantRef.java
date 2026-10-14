package org.hongtu.restaurant.DTO;

import lombok.Data;

/**
 * 按全局订单号定位到的租户。回调只取这两列。
 */
@Data
public class OrderTenantRef {
    private Long id;
    private Long tenantId;
}
