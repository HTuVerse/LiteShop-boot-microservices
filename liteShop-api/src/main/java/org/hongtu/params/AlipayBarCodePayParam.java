package org.hongtu.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付宝条码（被扫）支付请求参数。收银员扫顾客付款码发起扣款。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlipayBarCodePayParam {

    /** 订单所属租户。支付服务按该租户加载应用参数。 */
    private Long tenantId;

    /** 商户订单号，必填。 */
    private String outTradeNo;

    /** 订单标题，必填。 */
    private String subject;

    /** 付款金额（单位：元），必填。 */
    private BigDecimal totalAmount;

    /** 顾客付款码，扫码枪读取，一次性。 */
    private String authCode;
}
