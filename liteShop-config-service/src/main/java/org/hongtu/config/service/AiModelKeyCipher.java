package org.hongtu.config.service;
import org.hongtu.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
/** 使用稳定的服务端密钥加密 AI 与支付凭据，数据库和管理端均不保存明文。 */
@Component
public class AiModelKeyCipher {
    private final String secret;
    private final SecureRandom random = new SecureRandom();
    public AiModelKeyCipher(@Value("${liteshop.credentials.encryption-key:${liteshop.ai.config-encryption-key:}}") String secret) {
        this.secret = secret;
    }
    /** 每次使用独立随机 nonce，避免同一密钥生成相同密文。 */
    public String encrypt(String key) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, nonce).doFinal(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (BusinessException error) { throw error;
        } catch (Exception error) { throw new BusinessException(503, "配置凭据加密失败，请联系平台管理员"); }
    }
    /** 仅内部运行配置读取时解密，管理端读取无需解密。 */
    public String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) return "";
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encrypted));
            byte[] nonce = new byte[12]; buffer.get(nonce);
            byte[] data = new byte[buffer.remaining()]; buffer.get(data);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce).doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new BusinessException(503, "配置凭据无法读取，请重新保存对应配置"); }
    }
    private Cipher cipher(int mode, byte[] nonce) throws Exception {
        if (!StringUtils.hasText(secret)) throw new BusinessException(503, "服务端未配置凭据加密密钥");
        byte[] key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }
}
