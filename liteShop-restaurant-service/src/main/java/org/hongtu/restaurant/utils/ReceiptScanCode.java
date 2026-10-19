package org.hongtu.restaurant.utils;

import java.util.UUID;

/** 小票扫码出餐码，只携带全局唯一业务订单 ID。 */
public final class ReceiptScanCode {
    private static final String PREFIX = "LITESHOP:PREPARED:";

    private ReceiptScanCode() { }

    public static String forOrder(Long orderId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("订单 ID 无效");
        // 每次正式打印使用独立随机票据，防止猜测订单 ID 后伪造扫码内容。
        return PREFIX + orderId + ":" + UUID.randomUUID().toString().replace("-", "");
    }

    public static Long orderId(String content) {
        if (content == null || !content.startsWith(PREFIX)) return null;
        String[] parts = content.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 2 || !parts[1].matches("[0-9a-f]{32}")) return null;
        String value = parts[0];
        if (!value.matches("[1-9][0-9]{0,18}")) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
