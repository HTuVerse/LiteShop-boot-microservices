package org.hongtu.service;

import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 防止应用公钥误填到支付宝公钥字段后才在扣款响应时发现。 */
class AlipayKeyPairGuardTest {
    @Test
    void rejectsOwnApplicationPublicKeyBeforePayment() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var application = generator.generateKeyPair();
        var platform = generator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(application.getPrivate().getEncoded());
        String appPublic = Base64.getEncoder().encodeToString(application.getPublic().getEncoded());
        String alipayPublic = Base64.getEncoder().encodeToString(platform.getPublic().getEncoded());

        assertThrows(BusinessException.class,
                () -> AlipayKeyPairGuard.requireAlipayPublicKey(privateKey, appPublic));
        assertDoesNotThrow(() -> AlipayKeyPairGuard.requireAlipayPublicKey(privateKey, alipayPublic));
    }
}
