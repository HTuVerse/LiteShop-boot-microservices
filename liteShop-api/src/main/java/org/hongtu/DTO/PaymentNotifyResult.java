package org.hongtu.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付回调通知解析结果
 * 通用的支付回调数据封装，适用于所有业务模块
 * 
 * @author Ht
 * @since 2025-11-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotifyResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 商户订单号
     */
    private String outTradeNo;
    
    /**
     * 支付平台交易号
     * 微信：transaction_id
     * 支付宝：trade_no
     */
    private String transactionId;
    
    /**
     * 交易状态
     * 微信：SUCCESS-支付成功, REFUND-转入退款, NOTPAY-未支付, CLOSED-已关闭, REVOKED-已撤销, USERPAYING-用户支付中, PAYERROR-支付失败
     * 支付宝：TRADE_SUCCESS-交易支付成功, TRADE_FINISHED-交易结束不可退款
     */
    private String tradeState;
    
    /**
     * 交易状态描述
     */
    private String tradeStateDesc;
    
    /**
     * 支付完成时间
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    private String successTime;
    
    /**
     * 订单金额（分）
     */
    private Integer totalAmount;
    
    /**
     * 用户实际支付金额（分）
     */
    private Integer payerAmount;
    
    /**
     * 币种
     */
    private String currency;
    
    /**
     * 附加数据
     * 商户传入的自定义数据
     */
    private String attach;
    
    /**
     * 用户标识
     * 微信：openid
     * 支付宝：buyer_id
     */
    private String payerOpenid;
    
    /**
     * 是否支付成功
     */
    private Boolean isSuccess;
    
    /**
     * 支付方式
     * WECHAT：微信支付
     * ALIPAY：支付宝
     */
    private String paymentType;
}

