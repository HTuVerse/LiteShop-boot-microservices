package org.hongtu.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCancelRequest;
import com.alipay.api.request.AlipayTradePayRequest;
import com.alipay.api.response.AlipayTradeCancelResponse;
import com.alipay.api.response.AlipayTradePayResponse;
import org.hongtu.service.impl.AlipayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 支付宝条码支付（被扫）状态映射与撤单。 */
class AlipayBarCodeServiceTest {
    private final TenantPaymentClientFactory clients = mock(TenantPaymentClientFactory.class);
    private final AlipayClient alipayClient = mock(AlipayClient.class);
    private final AlipayServiceImpl service = new AlipayServiceImpl(clients);

    @BeforeEach
    void setUp() {
        when(clients.alipay(1L)).thenReturn(alipayClient);
    }

    @Test
    void barCodeSuccessMapsState() throws Exception {
        AlipayTradePayResponse response = new AlipayTradePayResponse();
        response.setCode("10000");
        response.setTradeNo("ali-txn-1");
        response.setOutTradeNo("RO1");
        when(alipayClient.execute(any(AlipayTradePayRequest.class))).thenReturn(response);

        Map<String, Object> out = service.createBarCodePay(1L, "RO1", "餐厅订单", new BigDecimal("12.00"), "281234");
        assertEquals("SUCCESS", out.get("state"));
        assertEquals("ali-txn-1", out.get("tradeNo"));
    }

    @Test
    void barCodeWaitingOnCode10003() throws Exception {
        AlipayTradePayResponse response = new AlipayTradePayResponse();
        response.setCode("10003");
        when(alipayClient.execute(any(AlipayTradePayRequest.class))).thenReturn(response);

        Map<String, Object> out = service.createBarCodePay(1L, "RO1", "餐厅订单", new BigDecimal("12.00"), "281234");
        assertEquals("WAITING", out.get("state"));
    }

    @Test
    void cancelTradeMapsRetryFlag() throws Exception {
        AlipayTradeCancelResponse response = new AlipayTradeCancelResponse();
        response.setCode("10000");
        response.setRetryFlag("N");
        when(alipayClient.execute(any(AlipayTradeCancelRequest.class))).thenReturn(response);

        Map<String, Object> out = service.cancelTrade(1L, "RO1");
        assertEquals("N", out.get("retryFlag"));
    }
}
