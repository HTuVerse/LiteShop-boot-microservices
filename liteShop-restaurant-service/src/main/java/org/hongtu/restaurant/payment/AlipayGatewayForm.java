package org.hongtu.restaurant.payment;

import org.hongtu.exception.BusinessException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使支付宝网页支付表单的提交网关与租户保存的沙箱开关一致。
 */
public final class AlipayGatewayForm {
    private static final String PRODUCTION = "https://openapi.alipay.com/gateway.do";
    private static final String SANDBOX = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private static final Pattern ACTION = Pattern.compile(
            "(?i)<form\\b[^>]*?\\baction\\s*=\\s*(['\"])(https://[a-z0-9.-]+/gateway\\.do)(?:\\?[^'\"]*)?\\1",
            Pattern.DOTALL);

    private AlipayGatewayForm() {
    }

    /** 仅修改表单 action 的网关前缀，签名参数和业务参数保持原样。 */
    public static String align(String form, boolean sandbox) {
        if (form == null) {
            throw new BusinessException("支付宝支付表单为空");
        }
        Matcher matcher = ACTION.matcher(form);
        if (!matcher.find()) {
            throw new BusinessException("支付宝支付表单缺少有效网关地址");
        }
        String current = matcher.group(2);
        if (!PRODUCTION.equals(current) && !SANDBOX.equals(current)) {
            throw new BusinessException("支付宝支付表单网关地址不受支持");
        }
        String expected = sandbox ? SANDBOX : PRODUCTION;
        if (expected.equals(current)) {
            return form;
        }
        return form.substring(0, matcher.start(2)) + expected + form.substring(matcher.end(2));
    }
}
