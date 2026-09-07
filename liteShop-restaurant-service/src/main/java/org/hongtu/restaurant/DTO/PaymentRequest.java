package org.hongtu.restaurant.DTO;

import lombok.Data;
import org.hongtu.restaurant.enums.PaymentTypeEnum;

/**
 * 支付请求DTO
 * 
 * @author Ht
 * @since 2025-10-31
 */
@Data
public class PaymentRequest {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 支付方式（1-微信支付 2-支付宝支付 3-现金支付 等）
     */
    private PaymentTypeEnum paymentType;
    
    /**
     * 支付场景（可选）
     * 例如：小程序、APP、H5等
     */
    private String paymentScene;
    
    /**
     * 用户openId（微信支付时需要）
     */
    private String openId;
    
    /**
     * 前端回调地址（可选）
     */
    private String returnUrl;
}

