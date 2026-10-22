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
     * MINI_PROGRAM、JSAPI、H5、PC、FACE_TO_FACE。
     * PC 微信返回 Native 二维码，FACE_TO_FACE 返回商家二维码；顾客付款码由收银台接口处理。
     */
    private String paymentScene;
    
    /**
     * 用户openId（微信支付时需要）
     */
    private String openId;

    /** 支付宝小程序静默授权码，由支付服务换取用户 ID。 */
    private String authCode;
    
    /**
     * 前端回调地址（可选）
     */
    private String returnUrl;
}

