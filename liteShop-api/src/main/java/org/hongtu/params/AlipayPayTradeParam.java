package org.hongtu.params;

import lombok.Data;

import java.math.BigDecimal;

/** 支付宝电脑网站和当面付预创建交易参数，由订单服务提供可信金额。 */
@Data
public class AlipayPayTradeParam {
    /** 订单所属租户。 */
    private Long tenantId;
    /** 商户订单号。 */
    private String outTradeNo;
    /** 金额，单位：元。 */
    private BigDecimal totalAmount;
    private String subject;
    private String notifyUrl;
    /** 电脑网站支付完成后的同源返回地址。 */
    private String returnUrl;
}
