package org.hongtu.restaurant.security;

import org.hongtu.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 桌台会话能力票据。匿名顾客只有持有服务端签发的短期票据，才能操作对应会话。
 */
@Service
public class TableSessionTokenService {

    public static final String HEADER_NAME = "X-Table-Session-Token";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TOKEN_TTL_SECONDS = 12 * 60 * 60;
    private static final String SESSION_TOKEN_VERSION = "v1";
    private static final String TABLE_TOKEN_VERSION = "t2";

    private final byte[] secret;
    private final long tableTokenTtlSeconds;

    public TableSessionTokenService(
            @Value("${liteshop.table-session.token-secret:${liteshop.internal-call.secret:}}") String secret,
            @Value("${liteshop.table-session.table-token-ttl-seconds:2592000}") long tableTokenTtlSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("桌台会话 token-secret 必须配置且不少于32个字符");
        }
        if (tableTokenTtlSeconds <= 0) {
            throw new IllegalStateException("桌台扫码票据有效期必须大于0");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tableTokenTtlSeconds = tableTokenTtlSeconds;
    }

    public String issue(Long tenantId, String sessionNo) {
        if (tenantId == null || tenantId <= 0 || sessionNo == null || sessionNo.isBlank()) {
            throw new IllegalArgumentException("无法为无效桌台会话签发票据");
        }
        long expiresAt = Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS;
        String payload = tenantId + ":" + sessionNo + ":" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return SESSION_TOKEN_VERSION + "." + encodedPayload + "." + sign(encodedPayload);
    }

    /**
     * 签发可吊销的桌台扫码票据。同一 tokenId 在有效期内可被多名客人重复使用，轮换 tokenId 后旧码立即失效。
     */
    public String issueTableToken(Long tenantId, Long storeId, Long tableId, String tokenId) {
        if (tenantId == null || tenantId <= 0 || storeId == null || storeId <= 0
                || tableId == null || tableId <= 0 || tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("无法为无效桌台签发扫码凭据");
        }
        long expiresAt = Instant.now().getEpochSecond() + tableTokenTtlSeconds;
        String payload = tenantId + ":" + storeId + ":" + tableId + ":" + expiresAt + ":" + tokenId;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return TABLE_TOKEN_VERSION + "." + encodedPayload + "." + sign(encodedPayload);
    }

    public Claims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "缺少桌台会话凭据");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !SESSION_TOKEN_VERSION.equals(parts[0])) {
            throw new BusinessException(401, "桌台会话凭据无效");
        }
        byte[] expected = sign(parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = parts[2].getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(401, "桌台会话凭据无效");
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] values = payload.split(":", 3);
            long tenantId = Long.parseLong(values[0]);
            String sessionNo = values[1];
            long expiresAt = Long.parseLong(values[2]);
            if (tenantId <= 0 || sessionNo.isBlank() || expiresAt < Instant.now().getEpochSecond()) {
                throw new BusinessException(401, "桌台会话凭据已失效");
            }
            return new Claims(tenantId, sessionNo, expiresAt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "桌台会话凭据无效");
        }
    }

    public Claims verifyForSession(String token, String sessionNo, Long currentTenantId) {
        Claims claims = verify(token);
        if (!claims.sessionNo().equals(sessionNo)
                || currentTenantId == null
                || !claims.tenantId().equals(currentTenantId)) {
            throw new BusinessException(403, "桌台会话凭据与当前租户或会话不匹配");
        }
        return claims;
    }

    public TableClaims verifyForTable(String token, Long tableId, Long currentTenantId,
                                      Long storeId, String expectedTokenId) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "缺少桌台扫码凭据");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !TABLE_TOKEN_VERSION.equals(parts[0])) {
            throw new BusinessException(401, "桌台扫码凭据无效");
        }
        if (!MessageDigest.isEqual(
                sign(parts[1]).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(401, "桌台扫码凭据无效");
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] values = payload.split(":", 5);
            long tenantId = Long.parseLong(values[0]);
            long signedStoreId = Long.parseLong(values[1]);
            long signedTableId = Long.parseLong(values[2]);
            long expiresAt = Long.parseLong(values[3]);
            String tokenId = values[4];
            if (tenantId <= 0 || signedStoreId <= 0 || signedTableId <= 0 || tokenId.isBlank()
                    || expiresAt < Instant.now().getEpochSecond()
                    || tableId == null || currentTenantId == null || storeId == null
                    || expectedTokenId == null || expectedTokenId.isBlank()
                    || signedTableId != tableId || tenantId != currentTenantId
                    || signedStoreId != storeId || !tokenId.equals(expectedTokenId)) {
                throw new BusinessException(403, "桌台扫码凭据与当前租户、门店或桌台不匹配");
            }
            return new TableClaims(tenantId, signedStoreId, signedTableId, expiresAt, tokenId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "桌台扫码凭据无效");
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("桌台会话凭据签名失败", e);
        }
    }

    public record Claims(Long tenantId, String sessionNo, long expiresAt) {
    }

    public record TableClaims(Long tenantId, Long storeId, Long tableId, long expiresAt, String tokenId) {
    }
}
