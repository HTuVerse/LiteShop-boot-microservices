package org.hongtu.restaurant.DTO;

import lombok.Data;

import java.util.List;

/**
 * 追加菜品请求
 * 用于在现有订单中追加新菜品（加菜场景）
 * 
 * @author System
 * @since 2024-11-24
 */
@Data
public class AddItemsRequest {


    /**
     * 订单id
     * */
    private Long orderId;

    /**
     * 会话编号（用于验证）
     */
    private String sessionNo;

    /**
     * 购物车商品列表（新增的菜品）
     */
    private List<CreateOrderRequest.CartItem> cartItems;

    /**
     * 备注信息
     */
    private String note;

    /**
     * 用户优惠券ID列表（加菜仅支持菜品免单券，其它类型将报错）
     */
    private List<Long> userCouponIds;
}

