package org.hongtu.tenant;

import org.hongtu.exception.TenantContextMissingException;

/** Request-scoped tenant context. Entry filters set it and must clear it. */
public final class TenantContext {
    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPER_ADMIN = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        if (tenantId == null) {
            TENANT.remove();
        } else {
            TENANT.set(tenantId);
        }
    }

    public static Long getTenantId() {
        Long tenantId = TENANT.get();
        if (tenantId == null) {
            throw new TenantContextMissingException("缺少租户上下文");
        }
        return tenantId;
    }

    public static Long getTenantIdOrNull() {
        return TENANT.get();
    }

    public static boolean hasTenant() {
        return TENANT.get() != null;
    }

    public static void setSuperAdmin(boolean superAdmin) {
        SUPER_ADMIN.set(superAdmin);
    }

    public static boolean isSuperAdmin() {
        return Boolean.TRUE.equals(SUPER_ADMIN.get());
    }

    public static void clear() {
        TENANT.remove();
        SUPER_ADMIN.remove();
    }
}