package org.hongtu.mall.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 后台商品保存参数。 */
@Data
public class MallProductRequest {
    private String name;
    private String description;
    private String imageUrl;
    private Long couponId;
    private Integer pointsPrice;
    private String pricingMode;
    private BigDecimal cashPrice;
    private Integer stock;
    private Integer status;
    private Integer sort;
}
