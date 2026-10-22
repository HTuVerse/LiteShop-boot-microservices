package org.hongtu.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 微信支付交易参数
 * 
 * @author Ht
 * @since 2025-10-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeChatPayTradeParam {

    /**
     * 订单所属租户。支付服务按该租户加载商户参数。
     */
    private Long tenantId;

    /**
     * 商户订单号，必填
     */
    private String outTradeNo;

    /**
     * 订单描述，必填
     */
    private String description;

    /**
     * 付款金额（单位：元），必填
     */
    private BigDecimal totalAmount;

    /**
     * 用户openid（小程序支付必填）
     */
    private String openid;

    /** H5 场景的用户终端 IP，由订单服务从请求获取。 */
    private String payerClientIp;

    /**
     * 回调url
     */
    private String notifyUrl;

    /** 回调所属业务；RESTAURANT 由支付服务按账户生成地址。 */
    private String callbackOwner;

    /**
     * 订单附加信息，可空
     */
    private String attach;

    /**
     * 订单优惠标记
     */
    private String goodsTag;

    /**
     * 支付超时时间（单位：分钟），默认30分钟
     */
    @Builder.Default
    private Integer timeExpire = 30;

    /**
     * 支付场景类型
     * JSAPI: 小程序/公众号支付
     * APP: APP支付
     * H5: H5支付
     * NATIVE: 扫码支付
     */
    @Builder.Default
    private String tradeType = "JSAPI";
}

