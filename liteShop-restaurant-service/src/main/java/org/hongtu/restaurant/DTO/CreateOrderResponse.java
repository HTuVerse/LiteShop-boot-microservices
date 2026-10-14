package org.hongtu.restaurant.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.enums.SceneTypeEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建订单响应DTO
 *
 * @author Ht
 * @since 2025-10-27
 */
@Data
public class CreateOrderResponse {

    /**
     * 订单ID
     */
    private Long id;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 餐桌ID
     */
    private Long tableId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 订单类型 0-在线点餐 ，1-预点餐，2-门店自提
     */
    private SceneTypeEnum orderType;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 实际支付金额
     */
    private BigDecimal actualAmount;

    /**
     * 支付状态：0-未支付，1-已支付，2-部分退款，3-全额退款，4-支付超时，5-取消支付，6-支付失败
     */
    private PaymentStatusEnum paymentStatus;

    /**
     * 订单履约状态：1-已下单，2-已接单，3-制作中，4-制作完成，5-已完成，6-已取消
     */
    private OrderStatusEnum status;

    /**
     * 订单备注
     */
    private String note;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 订单明细列表
     */
    private List<OrderItemVO> items;

    /**
     * 订单明细视图对象
     */
    @Data
    public static class OrderItemVO {
        /**
         * 明细ID
         */
        private Long id;

        /**
         * 菜品ID
         */
        private Long dishId;

        /**
         * 分类
         */
        private Long categoryId;

        /**
         * 单价
         */
        private BigDecimal price;

        /**
         * 数量
         */
        private Integer quantity;

        /**
         * 小计
         */
        private BigDecimal totalPrice;

        /**
         * 规格信息
         */
        private String specOptionsText;

        /**
         * 备注
         */
        private String note;
    }
}

