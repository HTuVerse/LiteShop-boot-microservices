package org.hongtu.restaurant.DTO;

import lombok.Data;

import java.util.List;

/** 优惠券试算请求；菜品券按购物车实购菜品及服务端价格计算。 */
@Data
public class CouponQuoteRequest {
    private Long storeId;
    private List<Long> userCouponIds;
    private List<CreateOrderRequest.CartItem> cartItems;
}
