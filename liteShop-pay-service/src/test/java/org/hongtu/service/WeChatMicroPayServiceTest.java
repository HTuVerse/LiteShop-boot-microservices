package org.hongtu.service;

import com.github.binarywang.wxpay.bean.request.WxPayMicropayRequest;
import com.github.binarywang.wxpay.bean.request.WxPayOrderReverseRequest;
import com.github.binarywang.wxpay.bean.result.WxPayMicropayResult;
import com.github.binarywang.wxpay.bean.result.WxPayOrderReverseResult;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import org.hongtu.exception.BusinessException;
import org.hongtu.service.impl.WeChatPayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 微信付款码（被扫）支付状态映射与金额校验。 */
class WeChatMicroPayServiceTest {
    private final TenantPaymentClientFactory clients = mock(TenantPaymentClientFactory.class);
    private final WxPayService wxPayService = mock(WxPayService.class);
    private final WeChatPayServiceImpl service = new WeChatPayServiceImpl(clients);

    @BeforeEach
    void setUp() {
        when(clients.wxMicroPay(1L)).thenReturn(wxPayService);
    }

    @Test
    void microPaySuccessMapsState() throws Exception {
        WxPayMicropayResult result = new WxPayMicropayResult();
        result.setTransactionId("wx-txn-1");
        result.setOutTradeNo("RO1");
        when(wxPayService.micropay(any(WxPayMicropayRequest.class))).thenReturn(result);

        Map<String, Object> out = service.createMicroPay(1L, "RO1", "餐饮订单", new BigDecimal("12.00"), "280123");
        assertEquals("SUCCESS", out.get("state"));
        assertEquals("wx-txn-1", out.get("transactionId"));
    }

    @Test
    void microPayUserPayingMapsState() throws Exception {
        WxPayException userPaying = WxPayException.newBuilder().errCode("USERPAYING").errCodeDes("用户支付中").build();
        when(wxPayService.micropay(any(WxPayMicropayRequest.class))).thenThrow(userPaying);

        Map<String, Object> out = service.createMicroPay(1L, "RO1", "餐饮订单", new BigDecimal("12.00"), "280123");
        assertEquals("USERPAYING", out.get("state"));
    }

    @Test
    void microPayRejectsNonTwoDecimalAmount() {
        assertThrows(BusinessException.class,
                () -> service.createMicroPay(1L, "RO1", "餐饮订单", new BigDecimal("12.345"), "280123"));
    }

    @Test
    void reverseMicroPayReturnsRecallFlag() throws Exception {
        WxPayOrderReverseResult reverse = new WxPayOrderReverseResult();
        reverse.setIsRecall("Y");
        when(wxPayService.reverseOrder(any(WxPayOrderReverseRequest.class))).thenReturn(reverse);

        Map<String, Object> out = service.reverseMicroPay(1L, "RO1");
        assertEquals(Boolean.TRUE, out.get("recall"));
    }
}
