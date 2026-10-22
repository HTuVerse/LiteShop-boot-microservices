package org.hongtu;

import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.payment.AlipayGatewayForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 支付宝网页表单网关与租户环境保持一致。 */
class AlipayGatewayFormTest {
    private static final String PRODUCTION = "https://openapi.alipay.com/gateway.do";
    private static final String SANDBOX = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    @Test
    void routesSignedFormToSandboxWithoutChangingParameters() {
        String form = "<form name=\"punchout_form\" method=\"post\" action=\"" + PRODUCTION
                + "?app_id=9021000151603001&sign=a%2Bb&notify_url=https%3A%2F%2Fexample.com\">"
                + "<input name=\"biz_content\" value=\"signed-content\"></form>";
        assertEquals(form.replace(PRODUCTION, SANDBOX), AlipayGatewayForm.align(form, true));
    }

    @Test
    void keepsMatchingGatewayAndRejectsUnknownGateway() {
        String form = "<form action='" + SANDBOX + "?app_id=1'>x</form>";
        assertEquals(form, AlipayGatewayForm.align(form, true));
        assertEquals(form.replace(SANDBOX, PRODUCTION), AlipayGatewayForm.align(form, false));
        assertThrows(BusinessException.class,
                () -> AlipayGatewayForm.align("<form action='https://other.example.com/gateway.do'>", true));
    }
}
