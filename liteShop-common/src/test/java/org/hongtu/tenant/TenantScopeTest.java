package org.hongtu.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantScopeTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantIgnore.suspend();
    }

    @Test
    void shouldRestoreGlobalSuperAdminAndNestedIgnoreDepth() {
        TenantContext.setSuperAdmin(true);
        TenantIgnore.open();
        TenantIgnore.open();

        String value = TenantScope.call(2001L, () -> {
            assertEquals(2001L, TenantContext.getTenantId());
            assertTrue(TenantContext.isSuperAdmin());
            int scopedIgnoreDepth = TenantIgnore.suspend();
            assertEquals(0, scopedIgnoreDepth);
            TenantIgnore.restore(scopedIgnoreDepth);
            return "ok";
        });

        assertEquals("ok", value);
        assertNull(TenantContext.getTenantIdOrNull());
        assertTrue(TenantContext.isSuperAdmin());
        int restoredIgnoreDepth = TenantIgnore.suspend();
        assertEquals(2, restoredIgnoreDepth);
        TenantIgnore.restore(restoredIgnoreDepth);
    }

    @Test
    void shouldRestoreContextWhenActionFails() {
        TenantContext.setTenantId(1001L);

        assertThrows(IllegalStateException.class,
                () -> TenantScope.run(2001L, () -> {
                    throw new IllegalStateException("boom");
                }));

        assertEquals(1001L, TenantContext.getTenantId());
    }
}
