package org.hongtu.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpVerifierTest {

    @Test
    void acceptsCurrentCodeAndRejectsBlank() throws Exception {
        String secret = "JBSWY3DPEHPK3PXP";
        assertTrue(TotpVerifier.matches(secret, currentCode(secret)));
        assertFalse(TotpVerifier.matches(secret, "000000"));
        assertFalse(TotpVerifier.matches(null, "123456"));
    }

    private String currentCode(String base32Secret) throws Exception {
        byte[] key = decode(base32Secret);
        long counter = System.currentTimeMillis() / 1000 / 30;
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1000000);
    }

    private byte[] decode(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[value.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < value.length(); i++) {
            buffer = (buffer << 5) | alphabet.indexOf(value.charAt(i));
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out;
    }
}
