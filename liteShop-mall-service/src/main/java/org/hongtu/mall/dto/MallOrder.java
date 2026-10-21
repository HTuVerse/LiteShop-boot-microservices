package org.hongtu.mall.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 积分兑换订单快照。 */
@Data
public class MallOrder {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long productId;
    private String productName;
    private Long couponId;
    private Integer pointsCost;
    private String status;
    private String requestKey;
    private Long userCouponId;
    private String failureReason;
    private LocalDateTime processingUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
