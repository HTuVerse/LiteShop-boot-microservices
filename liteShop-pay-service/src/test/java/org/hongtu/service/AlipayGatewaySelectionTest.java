package org.hongtu.service;

import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 支付宝应用环境必须决定 SDK 网关，不能由表单后处理修正。 */
class AlipayGatewaySelectionTest {
    @Test
    void sandboxApplicationUsesSandboxGateway() {
        assertEquals("https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                TenantPaymentClientFactory.alipayGateway(true));
        assertEquals("https://openapi.alipay.com/gateway.do",
                TenantPaymentClientFactory.alipayGateway(false));
        assertThrows(BusinessException.class, () -> TenantPaymentClientFactory.alipayGateway(null));
    }
}
