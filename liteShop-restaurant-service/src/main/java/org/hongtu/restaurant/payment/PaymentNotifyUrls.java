package org.hongtu.restaurant.payment;

import org.hongtu.exception.BusinessException;

/**
 * 给渠道回调地址追加租户，支付服务才能选中对应密钥。
 */
public final class PaymentNotifyUrls {
    private PaymentNotifyUrls() {
    }

    public static String withTenant(String configuredUrl, Long tenantId, String provider) {
        if (configuredUrl == null || configuredUrl.isBlank() || configuredUrl.contains("your-domain")
                || !configuredUrl.startsWith("https://")) {
            throw new BusinessException(provider + "支付回调地址未配置为 HTTPS 公网地址");
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(provider + "支付回调缺少租户");
        }
        String trimmed = configuredUrl.replaceAll("/+$", "");
        String expectedPath = "微信".equals(provider) ? "/wechatPay/notify" : "/alipayPay/notify";
        if (!trimmed.endsWith(expectedPath) && !trimmed.endsWith(expectedPath + "/" + tenantId)) {
            throw new BusinessException(provider + "支付回调地址路径应以 " + expectedPath + " 结尾");
        }
        String suffix = "/" + tenantId;
        return trimmed.endsWith(suffix) ? trimmed : trimmed + suffix;
    }

    /** 由支付通知地址推导退款通知地址，并带上同一租户。 */
    public static String wechatRefund(String payNotifyUrl, Long tenantId) {
        String pay = withTenant(payNotifyUrl, tenantId, "微信");
        return pay.replace("/wechatPay/notify/", "/wechatPay/refund-notify/");
    }
}
