package org.hongtu.restaurant.DTO;

import lombok.Data;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.hongtu.restaurant.enums.SceneTypeEnum;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收银台下单请求DTO
 *
 * @author Ht
 * @since 2025-01-27
 */
@Data
public class CashierOrderRequest {

    /**
     * 门店ID（可选，如果不传则使用默认门店）
     */
    private Long storeId;

    /**
     * 餐桌ID（可选，收银台场景一般不需要）
     */
    private Long tableId;

    /**
     * 用户ID（余额支付时必填）
     */
    private Long userId;

    /**
     * 会话编号（可选；桌位收银时由前端传入，用于关联订单与会话。
     * 注意：桌位收银应走“先提交订单/加菜、再桌位结算”流程，不要用本直接下单接口创建桌位订单）
     */
    private String sessionNo;

    /**
     * 订单类型：0-在线点餐，1-预点餐，2-门店自提
     */
    private SceneTypeEnum orderType;

    /**
     * 就餐人数（可选）
     */
    private Integer peopleCount;

    /**
     * 订单总金额
     */
    private BigDecimal totalPrice;

    /**
     * 整单折扣率（例如：0.8表示8折，1.0表示无折扣，null表示无折扣）
     */
    private BigDecimal discountRate;

    /**
     * 支付方式：1-微信，2-支付宝，3-现金，4-银行卡，5-线下支付
     * 用于扫码支付和直接下单
     */
    private PaymentTypeEnum paymentType;

    /**
     * 实收金额（现金支付时可能与订单金额不同，用于找零）
     */
    private BigDecimal actualAmount;

    /**
     * 购物车商品列表
     */
    private List<CartItem> cartItems;

    /**
     * 订单备注
     */
    private String note;

    /**
     * 是否打印小票（默认true）
     */
    private Boolean printReceipt = true;

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
