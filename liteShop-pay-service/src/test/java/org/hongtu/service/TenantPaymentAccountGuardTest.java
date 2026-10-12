package org.hongtu.service;

import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 租户支付运行参数完整性校验。 */
class TenantPaymentAccountGuardTest {

    @Test
    void rejectsDisabledOrIncompleteAccount() {
        assertThrows(BusinessException.class, () -> TenantPaymentAccountGuard.requireWeChat(null));

        TenantPaymentRuntimeDTO account = base();
        account.setMchId("mch-1");
        account.setMchSerialNo("serial-1");
        assertThrows(BusinessException.class, () -> TenantPaymentAccountGuard.requireWeChat(account));
    }

    @Test
    void requireWeChatMicroPayRejectsMissingV2Key() {
        TenantPaymentRuntimeDTO account = base();
        account.setMchId("mch-1");
        // 缺少 apiV2Key
        assertThrows(BusinessException.class, () -> TenantPaymentAccountGuard.requireWeChatMicroPay(account));
    }

    @Test
    void requireWeChatMicroPayAcceptsComplete() {
        TenantPaymentRuntimeDTO account = base();
        account.setMchId("mch-1");
        account.setApiV2Key("12345678901234567890123456789012");
        assertDoesNotThrow(() -> TenantPaymentAccountGuard.requireWeChatMicroPay(account));
    }

    @Test
    void acceptsCompleteChannelAccounts() {
        TenantPaymentRuntimeDTO wechat = base();
        wechat.setMchId("mch-1");
        wechat.setMchSerialNo("serial-1");
        wechat.setApiV3Key("api-v3-key");
        assertDoesNotThrow(() -> TenantPaymentAccountGuard.requireWeChat(wechat));

        TenantPaymentRuntimeDTO alipay = base();
        alipay.setAlipayPublicKey("alipay-public-key");
        assertDoesNotThrow(() -> TenantPaymentAccountGuard.requireAlipay(alipay));
    }

    private TenantPaymentRuntimeDTO base() {
        TenantPaymentRuntimeDTO account = new TenantPaymentRuntimeDTO();
        account.setEnabled(true);
        account.setAppId("app-1");
        account.setPrivateKey("private-key");
        return account;
    }
}
