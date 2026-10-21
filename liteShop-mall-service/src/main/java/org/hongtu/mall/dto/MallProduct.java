package org.hongtu.mall.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 租户积分兑换商品，目前仅支持优惠券。 */
@Data
public class MallProduct {
    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String imageUrl;
    private Long couponId;
    private Integer pointsPrice;
    /** POINTS、POINTS_CASH、CASH。 */
    private String pricingMode;
    /** 现金售价，单位元。 */
    private BigDecimal cashPrice;
    private Integer stock;
    /** 0 下架，1 上架。 */
    private Integer status;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
