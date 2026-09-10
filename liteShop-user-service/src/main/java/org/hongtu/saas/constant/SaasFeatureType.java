package org.hongtu.saas.constant;

import java.util.Locale;
import java.util.Set;

public final class SaasFeatureType {
    private static final Set<String> SUPPORTED = Set.of("BOOLEAN", "NUMBER", "QUOTA", "STRING", "ENUM");

    private SaasFeatureType() {}

    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean supported(String value) { return SUPPORTED.contains(normalize(value)); }

    /** QUOTA 是旧数据兼容别名，新建数值功能推荐使用 NUMBER。 */
    public static boolean numeric(String value) {
        String normalized = normalize(value);
        return "NUMBER".equals(normalized) || "QUOTA".equals(normalized);
    }

    public static boolean textual(String value) {
        String normalized = normalize(value);
        return "STRING".equals(normalized) || "ENUM".equals(normalized);
    }
}
