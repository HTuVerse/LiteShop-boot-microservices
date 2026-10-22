package org.hongtu.service;

import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 支付回调地址的租户拼接和退款路径校验。 */
class PaymentNotifyUrlsTest {
    @Test
    void restaurantWechatUrls() {
        String pay = PaymentNotifyUrls.withTenant(
                "https://pay.example.com/wechatPay/notify", 8L, "WECHAT");
        assertEquals("https://pay.example.com/wechatPay/notify/8", pay);
        assertEquals("https://pay.example.com/wechatPay/refund-notify/8",
                PaymentNotifyUrls.wechatRefund(pay));
    }

    @Test
    void rejectWrongCallbackPath() {
        assertThrows(BusinessException.class, () -> PaymentNotifyUrls.withTenant(
                "https://pay.example.com/alipayPay/notify", 8L, "WECHAT"));
    }
}
