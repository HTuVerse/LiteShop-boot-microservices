package org.hongtu.restaurant.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付响应DTO
 * 返回给前端用于调起支付
 * 
 * @author Ht
 * @since 2025-10-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 订单编号
     */
    private String orderNo;
    
    /**
     * 支付金额
     */
    private BigDecimal payAmount;
    
    /**
     * 支付方式
     */
    private Integer paymentType;
    
    /**
     * 预支付交易会话标识（微信支付返回）
     */
    private String prepayId;
    
    /**
     * 支付二维码内容（扫码支付）
     */
    private String qrCode;
    
    /**
     * 支付跳转URL（H5支付）
     */
    private String payUrl;
    
    /**
     * 小程序支付参数（微信小程序）
     */
    private MiniPayParams miniPayParams;
    
    /**
     * 支付超时时间（秒）
     */
    private Integer expireSeconds;
    
    /**
     * 小程序支付参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MiniPayParams {
        private String timeStamp;
        private String nonceStr;
        private String packageValue;  // package是关键字，用packageValue
        private String signType;
        private String paySign;
    }
}

