package org.hongtu.tenant;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantInterceptorTest {
    private static final String STATEMENT = "org.hongtu.Mapper.select";
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        TenantIgnore.suspend();
        StoreContext.clear();
        TenantContext.clear();
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    @Test
    void shouldRejectUnsignedInternalTenantHeader() {
        var request = request("/inner/user/1");
        request.addHeader(TenantConstants.HEADER_TENANT_ID, "1001");
        assertThrows(SecurityException.class, () -> new TenantInterceptor("test-secret")
                .preHandle(request, response, new Object()));
    }

    @Test
    void shouldAcceptSignedInternalTenantHeader() {
        var request = request("/inner/user/1");
        request.addHeader(TenantConstants.HEADER_TENANT_ID, "1001");
        request.addHeader(TenantConstants.HEADER_INTERNAL_CALL, "test-secret");
        assertTrue(new TenantInterceptor("test-secret").preHandle(request, response, new Object()));
        assertEquals(1001L, TenantContext.getTenantId());
    }

    @Test
    void internalEndpointsAndPrivilegedHeadersRequireCredentialsEvenWithoutTenant() {
        for (String header : List.of(TenantConstants.HEADER_SUPER_ADMIN, TenantConstants.HEADER_STORE_IDS)) {
            var request = request("/user/list");
            request.addHeader(header, "true");
            assertThrows(SecurityException.class, () -> new TenantInterceptor("test-secret")
                    .preHandle(request, response, new Object()));
        }
        assertThrows(SecurityException.class, () -> new TenantInterceptor("test-secret")
                .preHandle(request("/inner/user/roles"), response, new Object()));
    }

    @Test
    void blankConfiguredSecretNeverTrustsInternalHeaders() {
        var request = request("/inner/user/roles");
        request.addHeader(TenantConstants.HEADER_INTERNAL_CALL, "anything");
        assertThrows(SecurityException.class, () -> new TenantInterceptor("")
                .preHandle(request, response, new Object()));
    }

    @Test
    void shouldRejectInactivePublicTenant() {
        var request = request("/dish/listDishNoPage");
        request.addHeader(TenantConstants.HEADER_PUBLIC_TENANT_ID, "1001");
        assertThrows(SecurityException.class, () -> new TenantInterceptor("test-secret", id -> false)
                .preHandle(request, response, new Object()));
        assertNull(TenantContext.getTenantIdOrNull());
    }

    @Test
    void shouldEstablishPublicTenantContextForCustomerLogin() {
        var request = request("/user/Login");
        request.addHeader(TenantConstants.HEADER_PUBLIC_TENANT_ID, "1001");
        assertTrue(new TenantInterceptor("test-secret", id -> true).preHandle(request, response, new Object()));
        assertEquals(1001L, TenantContext.getTenantId());
        assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
    }

    @Test
    void shouldRejectNonPositiveTenantId() {
        var request = request("/dish/listDishNoPage");
        request.addHeader(TenantConstants.HEADER_PUBLIC_TENANT_ID, "0");
        assertThrows(IllegalArgumentException.class, () -> new TenantInterceptor("test-secret", id -> true)
                .preHandle(request, response, new Object()));
    }

    @Test
    void anonymousRequestNeverGetsDefaultTenantOrGlobalBypass() {
        assertTrue(new TenantInterceptor("test-secret").preHandle(request("/user/admin/Login"), response, new Object()));
        assertNull(TenantContext.getTenantIdOrNull());
        assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
    }

    @Test
    void selectedTenantSuperAdminRemainsTenantFilteredAndIgnoresPublicHeader() {
        SaSession session = new SaSession("test");
        session.set(TenantConstants.SESSION_SUPER_ADMIN, true);
        session.set(TenantConstants.SESSION_TENANT_ID, 1001L);
        var request = request("/user/list");
        request.addHeader(TenantConstants.HEADER_PUBLIC_TENANT_ID, "9999");
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);
            new TenantInterceptor("test-secret", id -> id.equals(1001L)).preHandle(request, response, new Object());
            assertEquals(1001L, TenantContext.getTenantId());
            assertTrue(TenantContext.isSuperAdmin());
            assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
        }
    }

    @Test
    void globalPlatformBypassIsClearedAfterRequest() {
        SaSession session = new SaSession("test");
        session.set(TenantConstants.SESSION_SUPER_ADMIN, true);
        var interceptor = new TenantInterceptor("test-secret");
        var request = request("/sys-tenant/page");
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);
            interceptor.preHandle(request, response, new Object());
            assertTrue(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
            interceptor.afterCompletion(request, response, new Object(), null);
            assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
            assertFalse(TenantContext.isSuperAdmin());
        }
    }

    @Test
    void sessionRestoresOnlyTheSelectedStoreAndFailsClosedWithoutSelection() {
        SaSession session = new SaSession("test");
        session.set(TenantConstants.SESSION_TENANT_ID, 1001L);
        session.set(TenantConstants.SESSION_STORE_RESTRICTED, true);
        session.set(TenantConstants.SESSION_STORE_IDS, List.of(12L, 13L));
        session.set(TenantConstants.SESSION_ACTIVE_STORE_ID, 13L);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);
            var interceptor = new TenantInterceptor("test-secret", id -> true);
            interceptor.preHandle(request("/storeInfo/list"), response, new Object());
            assertTrue(StoreContext.isRestricted());
            assertEquals(List.of(13L), StoreContext.getStoreIds());
            session.delete(TenantConstants.SESSION_ACTIVE_STORE_ID);
            session.set(TenantConstants.SESSION_STORE_IDS, List.of());
            interceptor.preHandle(request("/storeInfo/list"), response, new Object());
            assertTrue(StoreContext.isRestricted());
            assertTrue(StoreContext.getStoreIds().isEmpty());
        }
    }

    @Test
    void rejectedRequestCannotLeakPreviousThreadScope() {
        TenantContext.setTenantId(1001L);
        TenantContext.setSuperAdmin(true);
        TenantIgnore.open();
        StoreContext.setRestricted(true, List.of(12L));
        assertThrows(SecurityException.class, () -> new TenantInterceptor("test-secret")
                .preHandle(request("/inner/user/roles"), response, new Object()));
        assertNull(TenantContext.getTenantIdOrNull());
        assertFalse(TenantContext.isSuperAdmin());
        assertFalse(StoreContext.isRestricted());
        assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
    }

    @Test
    void platformCanExitDisabledTenantButCannotReadItsBusinessData() {
        SaSession session = new SaSession("test");
        session.set(TenantConstants.SESSION_SUPER_ADMIN, true);
        session.set(TenantConstants.SESSION_TENANT_ID, 1001L);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);
            var interceptor = new TenantInterceptor("test-secret", id -> false);
            assertThrows(SecurityException.class, () -> interceptor.preHandle(request("/user/list"), response, new Object()));
            assertTrue(interceptor.preHandle(request("/sys-tenant/exit"), response, new Object()));
        }
    }
}
