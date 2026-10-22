package org.hongtu.service;

import org.hongtu.exception.BusinessException;

import java.net.URI;

/** 渠道通知地址由支付服务按账户配置和租户生成。 */
public final class PaymentNotifyUrls {
    private PaymentNotifyUrls() {
    }

    /** 校验保存的公开地址，并补充回调验签所需的租户。 */
    public static String withTenant(String configuredUrl, Long tenantId, String channel) {
        String expected = "WECHAT".equals(channel) ? "/wechatPay/notify" : "/alipayPay/notify";
        if (tenantId == null || tenantId <= 0 || configuredUrl == null || configuredUrl.isBlank()) {
            throw new BusinessException("支付回调地址未配置");
        }
        try {
            URI uri = URI.create(configuredUrl);
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || configuredUrl.contains("your-domain")
                    || !(expected.equals(path) || (expected + "/" + tenantId).equals(path))) {
                throw new BusinessException("支付回调地址路径应以 " + expected + " 结尾");
            }
            return expected.equals(path) ? configuredUrl + "/" + tenantId : configuredUrl;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("支付回调地址不是有效的 HTTPS 地址");
        }
    }

    /** 用同一域名生成微信退款通知地址。 */
    public static String wechatRefund(String payNotifyUrl) {
        return payNotifyUrl.replace("/wechatPay/notify/", "/wechatPay/refund-notify/");
    }
}
