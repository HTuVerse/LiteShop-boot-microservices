package org.hongtu.service;

import org.hongtu.exception.BusinessException;

import java.security.KeyFactory;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** 在调用可能扣款的接口前识别误填的应用公钥。 */
final class AlipayKeyPairGuard {
    private AlipayKeyPairGuard() {
    }

    /** 支付宝公钥必须是平台公钥，不能是本应用私钥对应的应用公钥。 */
    static void requireAlipayPublicKey(String privateKey, String alipayPublicKey) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var privateRsa = factory.generatePrivate(
                    new PKCS8EncodedKeySpec(decode(privateKey)));
            var publicRsa = factory.generatePublic(
                    new X509EncodedKeySpec(decode(alipayPublicKey)));
            if (!(privateRsa instanceof RSAPrivateCrtKey appPrivate)
                    || !(publicRsa instanceof RSAPublicKey configuredPublic)) {
                throw new BusinessException("支付宝密钥不是有效的 RSA 密钥");
            }
            if (appPrivate.getModulus().equals(configuredPublic.getModulus())
                    && appPrivate.getPublicExponent().equals(configuredPublic.getPublicExponent())) {
                throw new BusinessException("支付宝公钥误填为应用公钥，请从当前支付宝应用的公钥模式页面获取支付宝平台公钥");
            }
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException("支付宝公钥或应用私钥格式无效；当前系统仅支持公钥模式");
        }
    }

    private static byte[] decode(String key) {
        if (key == null) {
            throw new IllegalArgumentException("密钥为空");
        }
        String base64 = key.replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
