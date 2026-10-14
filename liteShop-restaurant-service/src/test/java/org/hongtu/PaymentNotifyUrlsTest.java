package org.hongtu;

import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.payment.PaymentNotifyUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 多租户支付回调地址生成校验。 */
class PaymentNotifyUrlsTest {

    @Test
    void appendsTenantOnlyOnce() {
        String base = "https://pay.example.com/restaurant-order/wechatPay/notify";
        assertEquals(base + "/8", PaymentNotifyUrls.withTenant(base, 8L, "微信"));
        assertEquals(base + "/8", PaymentNotifyUrls.withTenant(base + "/8", 8L, "微信"));
    }

    @Test
    void derivesTenantRefundCallback() {
        assertEquals(
                "https://pay.example.com/restaurant-order/wechatPay/refund-notify/8",
                PaymentNotifyUrls.wechatRefund(
                        "https://pay.example.com/restaurant-order/wechatPay/notify", 8L));
    }

    @Test
    void rejectsInsecureOrTenantlessCallback() {
        assertThrows(BusinessException.class,
                () -> PaymentNotifyUrls.withTenant("http://pay.example.com/notify", 8L, "微信"));
        assertThrows(BusinessException.class,
                () -> PaymentNotifyUrls.withTenant("https://pay.example.com/notify", null, "微信"));
    }
}
