package org.hongtu.restaurant.utils;

/** 小票扫码出餐码，只携带全局唯一业务订单 ID。 */
public final class ReceiptScanCode {
    private static final String PREFIX = "LITESHOP:PREPARED:";

    private ReceiptScanCode() { }

    public static String forOrder(Long orderId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("订单 ID 无效");
        return PREFIX + orderId;
    }

    public static Long orderId(String content) {
        if (content == null || !content.startsWith(PREFIX)) return null;
        String value = content.substring(PREFIX.length());
        if (!value.matches("[1-9][0-9]{0,18}")) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
