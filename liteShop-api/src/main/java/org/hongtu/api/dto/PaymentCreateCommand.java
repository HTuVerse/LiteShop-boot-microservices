package org.hongtu.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 订单服务提交的支付创建命令，只包含业务金额和支付场景。 */
@Data
public class PaymentCreateCommand {
    /** 订单所属租户，用于选择支付账户。 */
    private Long tenantId;
    /** 商户订单号。 */
    private String orderNo;
    /** WECHAT 或 ALIPAY。 */
    private String channel;
    /** MINI_PROGRAM、JSAPI、H5、PC 或 FACE_TO_FACE。 */
    private String scene;
    /** 服务端核算的应付金额，单位元。 */
    private BigDecimal amount;
    /** 支付页面显示的订单标题。 */
    private String subject;
    /** 渠道回传的订单归属信息。 */
    private String attach;
    /** 微信 JSAPI 所需的用户标识。 */
    private String openid;
    /** 支付宝用户授权码。 */
    private String authCode;
    /** 支付完成后的页面返回地址。 */
    private String returnUrl;
    /** 微信 H5 的付款人终端 IP。 */
    private String payerClientIp;
}
