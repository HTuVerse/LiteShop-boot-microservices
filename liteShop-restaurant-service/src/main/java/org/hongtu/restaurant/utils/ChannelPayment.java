package org.hongtu.restaurant.utils;

import java.math.BigDecimal;

/**
 * 渠道查单结果。只有金额、币种、商户单号和渠道流水都匹配时才允许改本地订单。
 */
public record ChannelPayment(boolean paid, String transactionId, BigDecimal amount, String currency,
                             String outTradeNo, boolean merchantMatched) {

    public static ChannelPayment unpaid() {
        return new ChannelPayment(false, null, null, null, null, false);
    }

    /**
     * 主动查单是否可以进入支付完成。
     */
    public boolean accepted(String expectedOrderNo) {
        return paid && merchantMatched && amount != null && transactionId != null && !transactionId.isBlank()
                && "CNY".equalsIgnoreCase(currency) && expectedOrderNo != null && expectedOrderNo.equals(outTradeNo);
    }
}
