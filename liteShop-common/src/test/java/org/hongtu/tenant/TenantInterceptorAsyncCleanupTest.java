package org.hongtu.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantInterceptorAsyncCleanupTest {
    @AfterEach
    void cleanup() {
        TenantContext.clear();
        StoreContext.clear();
        TenantIgnore.suspend();
    }

    @Test
    void clearsThreadLocalsWhenMvcStartsAsyncProcessing() {
        TenantContext.setTenantId(1001L);
        TenantContext.setSuperAdmin(true);
        StoreContext.setRestricted(true, List.of(2001L));
        TenantIgnore.open();

        new TenantInterceptor("").afterConcurrentHandlingStarted(null, null, null);

        assertNull(TenantContext.getTenantIdOrNull());
        assertFalse(TenantContext.isSuperAdmin());
        assertFalse(StoreContext.isRestricted());
        assertTrue(StoreContext.getStoreIds().isEmpty());
    }
}
