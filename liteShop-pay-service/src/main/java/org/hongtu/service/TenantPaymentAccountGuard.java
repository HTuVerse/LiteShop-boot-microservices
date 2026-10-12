package org.hongtu.service;

import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.exception.BusinessException;
import org.springframework.util.StringUtils;

/**
 * 拒绝没有启用账户或密钥不完整的租户，不回退到全局商户。
 */
public final class TenantPaymentAccountGuard {
    private TenantPaymentAccountGuard() {
    }

    public static TenantPaymentRuntimeDTO requireWeChat(TenantPaymentRuntimeDTO account) {
        requireBase(account, "微信");
        if (!StringUtils.hasText(account.getMchId())
                || !StringUtils.hasText(account.getMchSerialNo())
                || !StringUtils.hasText(account.getApiV3Key())) {
            throw missing("微信");
        }
        return account;
    }

    /**
     * 校验微信付款码（被扫）所需参数：在基础参数外还需商户号和 32 字节 APIv2 密钥。
     */
    public static TenantPaymentRuntimeDTO requireWeChatMicroPay(TenantPaymentRuntimeDTO account) {
        requireBase(account, "微信");
        if (!StringUtils.hasText(account.getMchId())
                || !StringUtils.hasText(account.getApiV2Key())
                || account.getApiV2Key().getBytes(java.nio.charset.StandardCharsets.UTF_8).length != 32) {
            throw missing("微信付款码");
        }
        return account;
    }

    public static TenantPaymentRuntimeDTO requireAlipay(TenantPaymentRuntimeDTO account) {
        requireBase(account, "支付宝");
        if (!StringUtils.hasText(account.getAlipayPublicKey())) {
            throw missing("支付宝");
        }
        return account;
    }

    private static void requireBase(TenantPaymentRuntimeDTO account, String channel) {
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())
                || !StringUtils.hasText(account.getAppId())
                || !StringUtils.hasText(account.getPrivateKey())) {
            throw missing(channel);
        }
    }

    private static BusinessException missing(String channel) {
        return new BusinessException("当前租户未配置可用的" + channel + "支付账户");
    }
}
