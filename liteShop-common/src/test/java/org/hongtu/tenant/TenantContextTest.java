package org.hongtu.tenant;

import org.hongtu.exception.TenantContextMissingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRejectTenantSqlWithoutContext() {
        assertThrows(TenantContextMissingException.class, TenantContext::getTenantId);
        assertNull(TenantContext.getTenantIdOrNull());
    }

    @Test
    void shouldReturnExplicitTenant() {
        TenantContext.setTenantId(1001L);
        assertEquals(1001L, TenantContext.getTenantId());
    }
}
