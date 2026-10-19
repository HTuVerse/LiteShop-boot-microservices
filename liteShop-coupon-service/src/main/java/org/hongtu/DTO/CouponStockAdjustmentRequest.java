package org.hongtu.DTO;

import lombok.Data;

/** 管理员补减优惠券库存的请求。 */
@Data
public class CouponStockAdjustmentRequest {
    private Long couponId;
    /** 正数补库，负数减库。 */
    private Integer delta;
    private String reason;
}
