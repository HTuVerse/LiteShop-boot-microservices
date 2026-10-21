package org.hongtu.mall.dto;

import lombok.Data;
import java.math.BigDecimal;
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
    /** 下单时的定价快照。 */
    private String pricingMode;
    private BigDecimal cashAmount;
    /** NONE、UNPAID、PAID、REFUNDING、REFUNDED。 */
    private String paymentStatus;
    private String payOutTradeNo;
    private String paymentTxnId;
    private String refundNo;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;
    private String status;
    private String requestKey;
    private Long userCouponId;
    private String failureReason;
    private LocalDateTime processingUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
