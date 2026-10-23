package org.hongtu.DAO;

import lombok.Data;

/** 批量查询优惠券可叠加配对时的一条有向关系。 */
@Data
public class CouponPartnerRow {
    private Long couponId;
    private Long partnerId;
}
