package org.hongtu.tenant;

import java.util.function.Supplier;

/**
 * 在当前线程临时切换到指定租户，并在结束时完整恢复原租户及旁路状态。
 * <p>
 * 主要用于平台超级管理员在全局模式下，对一个已确定归属租户的目标执行写操作。
 */
public final class TenantScope {

    private TenantScope() {
    }

    public static void run(Long tenantId, Runnable action) {
        call(tenantId, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T call(Long tenantId, Supplier<T> action) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("目标租户ID无效");
        }

        Long previousTenantId = TenantContext.getTenantIdOrNull();
        boolean previousSuperAdmin = TenantContext.isSuperAdmin();
        int previousIgnoreDepth = TenantIgnore.suspend();
        TenantContext.clear();
        TenantContext.setTenantId(tenantId);
        TenantContext.setSuperAdmin(previousSuperAdmin);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
            if (previousTenantId != null) {
                TenantContext.setTenantId(previousTenantId);
            }
            TenantContext.setSuperAdmin(previousSuperAdmin);
            TenantIgnore.restore(previousIgnoreDepth);
        }
    }
}
