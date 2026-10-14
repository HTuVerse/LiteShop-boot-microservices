package org.hongtu;

import org.hongtu.restaurant.utils.ChannelPayment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主动查单必须同时匹配金额、币种、商户和渠道流水，缺一项就不能改本地订单。
 */
class ChannelPaymentTest {

    @Test
    void acceptsMatchedPaidTrade() {
        ChannelPayment payment = new ChannelPayment(true, "4200001", new BigDecimal("12.00"),
                "CNY", "RO1", true);
        assertTrue(payment.accepted("RO1"));
    }

    @Test
    void rejectsAmountCurrencyOrMerchantMismatch() {
        assertFalse(new ChannelPayment(true, "4200001", null, "CNY", "RO1", true).accepted("RO1"));
        assertFalse(new ChannelPayment(true, "4200001", new BigDecimal("12.00"), "USD", "RO1", true).accepted("RO1"));
        assertFalse(new ChannelPayment(true, "4200001", new BigDecimal("12.00"), "CNY", "OTHER", true).accepted("RO1"));
        assertFalse(new ChannelPayment(true, " ", new BigDecimal("12.00"), "CNY", "RO1", false).accepted("RO1"));
        assertFalse(ChannelPayment.unpaid().accepted("RO1"));
    }
}
