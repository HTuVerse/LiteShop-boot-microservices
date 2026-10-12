package org.hongtu;

import org.hongtu.Result.Result;
import org.hongtu.api.client.PayFeignClient;
import org.hongtu.params.AlipayBarCodePayParam;
import org.hongtu.params.WeChatMicroPayParam;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.hongtu.restaurant.payment.ScanPayCharger;
import org.hongtu.restaurant.payment.ScanPayOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 付款码扣款编排：状态映射、轮询与超时撤单。 */
class ScanPayChargerTest {
    private final PayFeignClient payFeignClient = mock(PayFeignClient.class);
    // 测试用无等待 sleeper，避免轮询真实耗时
    private final ScanPayCharger charger = new ScanPayCharger(payFeignClient, millis -> { });

    private Map<String, Object> state(String state, String key, String value) {
        Map<String, Object> map = new HashMap<>();
        map.put("state", state);
        if (key != null) {
            map.put(key, value);
        }
        return map;
    }

    @Test
    void wechatSuccessReturnsPaid() {
        when(payFeignClient.createWeChatMicroPay(any(WeChatMicroPayParam.class)))
                .thenReturn(Result.success(state("SUCCESS", "transactionId", "wx-1")));

        ScanPayOutcome outcome = charger.charge(PaymentTypeEnum.WECHAT, 1L, "RO1", "餐饮订单", new BigDecimal("12.00"), "280");
        assertTrue(outcome.paid());
        assertEquals("wx-1", outcome.transactionId());
    }

    @Test
    void waitingThenSuccessByPolling() {
        when(payFeignClient.createWeChatMicroPay(any(WeChatMicroPayParam.class)))
                .thenReturn(Result.success(state("USERPAYING", null, null)));
        when(payFeignClient.queryWeChatMicroPay(eq(1L), eq("RO1")))
                .thenReturn(Result.success(state("SUCCESS", "transactionId", "wx-2")));

        ScanPayOutcome outcome = charger.charge(PaymentTypeEnum.WECHAT, 1L, "RO1", "餐饮订单", new BigDecimal("12.00"), "280");
        assertTrue(outcome.paid());
        assertEquals("wx-2", outcome.transactionId());
    }

    @Test
    void timeoutReversesAndReturnsUnpaid() {
        when(payFeignClient.createWeChatMicroPay(any(WeChatMicroPayParam.class)))
                .thenReturn(Result.success(state("USERPAYING", null, null)));
        when(payFeignClient.queryWeChatMicroPay(eq(1L), eq("RO1")))
                .thenReturn(Result.success(state("USERPAYING", null, null)));
        when(payFeignClient.reverseWeChatMicroPay(eq(1L), eq("RO1")))
                .thenReturn(Result.success(new HashMap<>()));

        ScanPayOutcome outcome = charger.charge(PaymentTypeEnum.WECHAT, 1L, "RO1", "餐饮订单", new BigDecimal("12.00"), "280");
        assertFalse(outcome.paid());
        verify(payFeignClient, times(1)).reverseWeChatMicroPay(1L, "RO1");
    }

    @Test
    void alipayBarCodeSuccess() {
        Map<String, Object> created = new HashMap<>();
        created.put("state", "SUCCESS");
        created.put("tradeNo", "ali-1");
        when(payFeignClient.createAlipayBarCodePay(any(AlipayBarCodePayParam.class)))
                .thenReturn(Result.success(created));

        ScanPayOutcome outcome = charger.charge(PaymentTypeEnum.ALIPAY, 1L, "RO1", "餐厅订单", new BigDecimal("12.00"), "281");
        assertTrue(outcome.paid());
        assertEquals("ali-1", outcome.transactionId());
    }

    @Test
    void failReturnsUnpaidWithMessage() {
        Map<String, Object> created = new HashMap<>();
        created.put("state", "FAIL");
        created.put("msg", "付款码无效");
        when(payFeignClient.createWeChatMicroPay(any(WeChatMicroPayParam.class)))
                .thenReturn(Result.success(created));

        ScanPayOutcome outcome = charger.charge(PaymentTypeEnum.WECHAT, 1L, "RO1", "餐饮订单", new BigDecimal("12.00"), "280");
        assertFalse(outcome.paid());
        assertEquals("付款码无效", outcome.message());
    }
}
