package org.hongtu.mall.dto;

import lombok.Data;

/** 后台商品保存参数。 */
@Data
public class MallProductRequest {
    private String name;
    private String description;
    private String imageUrl;
    private Long couponId;
    private Integer pointsPrice;
    private Integer stock;
    private Integer status;
    private Integer sort;
}
