package org.hongtu.service.impl;

import feign.RequestTemplate;
import org.hongtu.api.config.FeignTokenRelayInterceptor;
import org.hongtu.tenant.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TenantRelayTest {
    @AfterEach
    void clear() {
        TenantContext.clear();
        TenantIgnore.suspend();
        StoreContext.clear();
    }

    @Test
    void relayRoundTripPreservesSelectedTenantAndStoreScope() {
        TenantContext.setTenantId(1001L);
        StoreContext.setRestricted(true, List.of(11L, 12L));
        RequestTemplate template = new RequestTemplate();
        template.header(TenantConstants.HEADER_SUPER_ADMIN, "true");
        template.header(TenantConstants.HEADER_STORE_IDS, "9999");
        new FeignTokenRelayInterceptor("test-secret").apply(template);
        var request = new MockHttpServletRequest("GET", "/inner/user/1");
        template.headers().forEach((key, values) -> values.forEach(value -> request.addHeader(key, value)));
        new TenantInterceptor("test-secret").preHandle(request, new MockHttpServletResponse(), new Object());
        assertEquals(1001L, TenantContext.getTenantId());
        assertFalse(TenantContext.isSuperAdmin());
        assertTrue(StoreContext.isRestricted());
        assertEquals(List.of(11L, 12L), StoreContext.getStoreIds());
    }

    @Test
    void globalInternalLookupStillCarriesCredentialWithoutInventingTenant() {
        RequestTemplate template = new RequestTemplate();
        new FeignTokenRelayInterceptor("test-secret").apply(template);
        assertEquals(List.of("test-secret"), List.copyOf(template.headers().get(TenantConstants.HEADER_INTERNAL_CALL)));
        assertFalse(template.headers().containsKey(TenantConstants.HEADER_TENANT_ID));
    }

    @Test
    void missingSecretFailsBeforeSendingRequest() {
        assertThrows(IllegalStateException.class, () -> new FeignTokenRelayInterceptor("").apply(new RequestTemplate()));
    }
}