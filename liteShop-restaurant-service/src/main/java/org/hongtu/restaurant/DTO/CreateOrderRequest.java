package org.hongtu.restaurant.DTO;

import lombok.Data;
import org.hongtu.restaurant.enums.SceneTypeEnum;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建订单请求DTO
 *
 * @author Ht
 * @since 2025-10-27
 */
@Data
public class CreateOrderRequest {

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 餐桌ID（堂食必填）
     */
    private Long tableId;

    /**
     * 用户ID（可选，未登录时为空）
     */
    private Long userId;

    /**
     * 会话编号（共享购物车填写）
     */
    private String sessionNo;

    /**
     * 订单类型：0-在线点餐 ，1-预点餐，2-门店自提
     */
    private SceneTypeEnum orderType;

    /**
     * 就餐人数
     */
    private Integer peopleCount;

    /**
     * 订单总金额
     */
    private BigDecimal totalPrice;

    /**
     * 购物车商品列表
     */
    private List<CartItem> cartItems;

    /**
     * 订单备注
     */
    private String note;

    /**
     * 使用的用户优惠券ID列表（可选，不使用优惠券时为null或空列表，支持多张优惠券叠加）
     */
    private List<Long> userCouponIds;

    /**
     * 购物车商品项
     */
    @Data
    public static class CartItem {

        /**
         * 唯一ID（包含规格信息）
         */
        private String cartItemId;

        /**
         * 菜品ID
         */
        private Long dishId;

        /**
         * 分类ID
        */
        private Long categoryId;

        /**
         * 菜品名称
         */
        private String dishName;

        /**
         * 价格（含规格价格）
         */
        private BigDecimal price;

        /**
         * 基础价格
         */
        private BigDecimal basePrice;

        /**
         * 规格额外价格
         */
        private BigDecimal extraPrice;

        /**
         * 分类名称
         */
        private String categoryName;

        /**
         * 规格文本（用于显示）
         */
        private String specText;

        /**
         * 数量
         */
        private Integer quantity;

        /**
         * 是否赠送（0-否，1-是）
         */
        private Integer isGift;
    }

}
