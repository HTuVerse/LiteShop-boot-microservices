package org.hongtu.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * RFC 6238 动态口令校验，允许前后一个 30 秒窗口。
 */
public final class TotpVerifier {

    private TotpVerifier() {
    }

    public static boolean matches(String base32Secret, String code) {
        if (base32Secret == null || base32Secret.isBlank() || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key = decodeBase32(base32Secret);
        long step = System.currentTimeMillis() / 1000 / 30;
        for (long offset = -1; offset <= 1; offset++) {
            if (code.equals(hotp(key, step + offset))) {
                return true;
            }
        }
        return false;
    }

    private static String hotp(byte[] key, long counter) {
        try {
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
        } catch (GeneralSecurityException exception) {
            return "";
        }
    }

    private static byte[] decodeBase32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase();
        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[normalized.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int val = alphabet.indexOf(normalized.charAt(i));
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        if (index == out.length) {
            return out;
        }
        byte[] trimmed = new byte[index];
        System.arraycopy(out, 0, trimmed, 0, index);
        return trimmed;
    }
}
