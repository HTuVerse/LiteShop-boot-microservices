package org.hongtu.mall.dto;

import lombok.Data;
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
    private Integer stock;
    /** 0 下架，1 上架。 */
    private Integer status;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
