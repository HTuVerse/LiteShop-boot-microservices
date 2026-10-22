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

    /** 沙箱模式下前端应调用模拟支付确认接口，不调用微信收银台。 */
    private Boolean sandbox;
    
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

    /** 实际支付场景，供前端选择调起方式。 */
    private String paymentScene;
    
    /**
     * 预支付交易会话标识（微信支付返回）
     */
    private String prepayId;
    
    /** 支付二维码原始内容，前端据此绘制二维码。 */
    private String qrCode;
    
    /** 微信 H5 为跳转 URL；支付宝 H5/PC 为历史兼容的 HTML 表单。 */
    private String payUrl;

    /** 支付宝 H5/电脑网站的签名 HTML 表单；payUrl 暂保留同值兼容旧调用方。 */
    private String payForm;

    /** 支付宝小程序交易号，传给 uni.requestPayment.orderInfo。 */
    private String tradeNo;
    
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
        /** 微信网页 JSAPI 调起支付需传当前应用 ID。 */
        private String appId;
        private String timeStamp;
        private String nonceStr;
        private String packageValue;  // package是关键字，用packageValue
        private String signType;
        private String paySign;
    }
}

