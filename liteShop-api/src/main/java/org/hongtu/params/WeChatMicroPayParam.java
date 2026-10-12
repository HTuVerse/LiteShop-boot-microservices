package org.hongtu.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 微信付款码（被扫）支付请求参数。收银员扫顾客付款码发起扣款。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeChatMicroPayParam {

    /** 订单所属租户。支付服务按该租户加载商户参数。 */
    private Long tenantId;

    /** 商户订单号，必填。 */
    private String outTradeNo;

    /** 订单描述，必填。 */
    private String description;

    /** 付款金额（单位：元），必填。 */
    private BigDecimal totalAmount;

    /** 顾客付款码，扫码枪读取，一次性，约 1 分钟有效。 */
    private String authCode;
}
