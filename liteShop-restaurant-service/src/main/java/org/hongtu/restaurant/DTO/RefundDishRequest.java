package org.hongtu.restaurant.DTO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退菜请求DTO
 * 
 * @author Ht
 * @since 2026-02-15
 */
@Data
public class RefundDishRequest {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 退菜项列表
     */
    private List<RefundItem> refundItems;
    
    /**
     * 退菜原因
     */
    private String refundReason;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 退菜项明细
     */
    @Data
    public static class RefundItem {
        /**
         * 订单项ID
         */
        private Long orderItemId;
        
        /**
         * 退菜数量
         */
        private Integer refundQuantity;
        
        /**
         * 退款金额（可选，如果不传则自动计算）
         */
        private BigDecimal refundAmount;
    }
}
