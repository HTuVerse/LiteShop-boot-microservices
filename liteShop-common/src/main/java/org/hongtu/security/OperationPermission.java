package org.hongtu.security;

import cn.dev33.satoken.stp.StpUtil;
import java.util.Objects;

/** Additional checks for sensitive fields in otherwise permitted composite requests. */
public final class OperationPermission {
    private OperationPermission() {}

    public static void requireAny(String... codes) {
        if (!StpUtil.hasRole("SUPER_ADMIN")) StpUtil.checkPermissionOr(codes);
    }

    public static void requireChange(String code, Object previous, Object requested) {
        if (requested != null && !Objects.equals(previous, requested)) requireAny(code);
    }

    public static void order(String action) {
        requireAny("restaurant:order:" + action, "operations:direct-cashier:" + action,
                "operations:table-cashier:" + action, "operations:console:" + action);
    }
}
