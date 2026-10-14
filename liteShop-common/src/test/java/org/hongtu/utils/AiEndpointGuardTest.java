package org.hongtu.utils;

import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型地址守卫不访问外网。
 */
class AiEndpointGuardTest {

    @Test
    void rejectsMetadataHostsAndNonPublicAddresses() throws UnknownHostException {
        AiEndpointGuard guard = new AiEndpointGuard(host -> {
            throw new UnknownHostException(host);
        });
        assertThrows(BusinessException.class, () -> guard.normalize("https://metadata.google.internal/v1"));
        assertThrows(BusinessException.class, () -> guard.normalize("http://api.example.com/v1"));

        byte[] uniqueLocal = new byte[16];
        uniqueLocal[0] = (byte) 0xfd;
        assertTrue(AiEndpointGuard.isBlocked(InetAddress.getByAddress(uniqueLocal)));
        assertTrue(AiEndpointGuard.isBlocked(InetAddress.getByAddress(new byte[]{100, 100, 100, (byte) 200})));
    }

    @Test
    void acceptsPublicHttpsAndAppendsApiPrefix() {
        AiEndpointGuard guard = new AiEndpointGuard(host -> new InetAddress[]{
                InetAddress.getByAddress(host, new byte[]{1, 1, 1, 1})
        });
        assertEquals("https://api.example.com/v1", guard.normalize("https://api.example.com"));
        assertEquals("", guard.normalize("  "));
    }
}
