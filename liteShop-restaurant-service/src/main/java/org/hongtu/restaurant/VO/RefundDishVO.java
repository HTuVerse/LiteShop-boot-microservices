package org.hongtu.restaurant.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退菜结果VO
 * 
 * @author Ht
 * @since 2026-02-15
 */
@Data
public class RefundDishVO {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 订单编号
     */
    private String orderNo;
    
    /**
     * 退菜总金额
     */
    private BigDecimal totalRefundAmount;
    
    /**
     * 退菜项明细列表
     */
    private List<RefundItemDetail> refundItems;
    
    /**
     * 退菜后订单总金额
     */
    private BigDecimal newOrderTotalPrice;
    
    /**
     * 退菜原因
     */
    private String refundReason;
    
    /**
     * 操作人姓名
     */
    private String operatorName;
    
    /**
     * 退菜时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime refundTime;
    
    /**
     * 退菜项明细
     */
    @Data
    public static class RefundItemDetail {
        /**
         * 订单项ID
         */
        private Long orderItemId;
        
        /**
         * 菜品ID
         */
        private Long dishId;
        
        /**
         * 菜品名称
         */
        private String dishName;
        
        /**
         * 原数量
         */
        private Integer originalQuantity;
        
        /**
         * 退菜数量
         */
        private Integer refundQuantity;
        
        /**
         * 剩余数量
         */
        private Integer remainingQuantity;
        
        /**
         * 单价
         */
        private BigDecimal price;
        
        /**
         * 退款金额
         */
        private BigDecimal refundAmount;
        
        /**
         * 规格选项
         */
        private String specOptionsText;
    }
}
