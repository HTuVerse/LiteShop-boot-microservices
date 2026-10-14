package org.hongtu.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 渠道退款通知或查单核对结果。业务服务据此校验退款单后再入账。
 */
@Data
public class PaymentRefundNotifyResult {
    private String outRefundNo;
    private String outTradeNo;
    /** 渠道退款单号。 */
    private String channelRefundId;
    /** 退款金额，单位：元。 */
    private BigDecimal refundAmount;
    /** SUCCESS、PROCESSING、FAILED。 */
    private String status;
    private String appId;
    private String mchId;
    /** 通知中的商户是否与本次验签使用的租户账户一致。 */
    private Boolean merchantMatched;
}
