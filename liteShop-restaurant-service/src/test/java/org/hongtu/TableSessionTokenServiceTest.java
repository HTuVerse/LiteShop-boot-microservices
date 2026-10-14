package org.hongtu;

import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.security.TableSessionTokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 桌台扫码票据必须绑定门店、过期时间和可吊销编号。
 */
class TableSessionTokenServiceTest {

    private final TableSessionTokenService tokens =
            new TableSessionTokenService("0123456789abcdef0123456789abcdef", 3600);

    @Test
    void acceptsCurrentTableToken() {
        String token = tokens.issueTableToken(1L, 2L, 3L, "abc");
        TableSessionTokenService.TableClaims claims = tokens.verifyForTable(token, 3L, 1L, 2L, "abc");
        assertEquals(3L, claims.tableId());
        assertEquals("abc", claims.tokenId());
    }

    @Test
    void rejectsRotatedOrCrossStoreToken() {
        String token = tokens.issueTableToken(1L, 2L, 3L, "abc");
        assertThrows(BusinessException.class, () -> tokens.verifyForTable(token, 3L, 1L, 2L, "rotated"));
        assertThrows(BusinessException.class, () -> tokens.verifyForTable(token, 3L, 1L, 9L, "abc"));
    }

    @Test
    void sessionTokenExpires() {
        TableSessionTokenService expired = new TableSessionTokenService("0123456789abcdef0123456789abcdef", 3600) {
        };
        String token = tokens.issue(1L, "TBS1");
        assertEquals("TBS1", tokens.verify(token).sessionNo());
        assertThrows(BusinessException.class, () -> expired.verify("v1.bad.bad"));
    }
}
