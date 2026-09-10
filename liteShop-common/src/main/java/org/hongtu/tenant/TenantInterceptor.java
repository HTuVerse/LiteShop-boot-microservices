package org.hongtu.tenant;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** HTTP 请求使用服务端会话或经过验证的入口信息建立租户与门店范围。 */
public class TenantInterceptor implements AsyncHandlerInterceptor {
    private final String internalCallSecret;
    private final TenantAccessValidator tenantAccessValidator;

    public TenantInterceptor(String internalCallSecret) {
        this(internalCallSecret, tenantId -> false);
    }

    public TenantInterceptor(String internalCallSecret, TenantAccessValidator tenantAccessValidator) {
        this.internalCallSecret = internalCallSecret == null ? "" : internalCallSecret;
        this.tenantAccessValidator = tenantAccessValidator == null ? tenantId -> false : tenantAccessValidator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        clearScope();
        try {
            String path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (path != null && contextPath != null && !contextPath.isEmpty()) {
                path = path.substring(contextPath.length());
            }
            boolean internal = "/inner".equals(path) || (path != null && path.startsWith("/inner/"));
            if (internal || hasInternalHeaders(request)) {
                verifyInternalCall(request.getHeader(TenantConstants.HEADER_INTERNAL_CALL));
                String value = request.getHeader(TenantConstants.HEADER_TENANT_ID);
                Long tenantId = hasText(value) ? parseId(value) : null;
                boolean superAdmin = Boolean.parseBoolean(request.getHeader(TenantConstants.HEADER_SUPER_ADMIN));
                establishScope(tenantId, superAdmin,
                        Boolean.parseBoolean(request.getHeader(TenantConstants.HEADER_STORE_RESTRICTED)),
                        parseStoreIds(request.getHeader(TenantConstants.HEADER_STORE_IDS)));
                return true;
            }

            if (isLoginSafely()) {
                SaSession session = StpUtil.getSession();
                Object value = session.get(TenantConstants.SESSION_TENANT_ID);
                Long tenantId = value == null ? null : parseId(value.toString());
                boolean superAdmin = Boolean.parseBoolean(String.valueOf(session.get(TenantConstants.SESSION_SUPER_ADMIN)));
                // 允许平台账号从已停用的商户范围退出，以及任何账号注销。
                boolean leavingScope = "/user/logout".equals(path)
                        || (superAdmin && "/sys-tenant/exit".equals(path));
                if (tenantId != null && !leavingScope) {
                    requireActiveTenant(tenantId);
                }
                establishScope(tenantId, superAdmin,
                        Boolean.parseBoolean(String.valueOf(session.get(TenantConstants.SESSION_STORE_RESTRICTED))),
                        selectedStoreIds(session));
                return true;
            }

            String publicTenant = request.getHeader(TenantConstants.HEADER_PUBLIC_TENANT_ID);
            if (hasText(publicTenant)) {
                Long tenantId = parseId(publicTenant);
                requireActiveTenant(tenantId);
                TenantContext.setTenantId(tenantId);
            }
            // 管理端预认证在业务层显式使用 TenantIgnore；其他匿名请求没有默认租户或全局旁路。
            return true;
        } catch (RuntimeException error) {
            clearScope(); // preHandle 抛错时 MVC 不会调用本拦截器的 afterCompletion。
            throw error;
        }
    }

    private List<Long> selectedStoreIds(SaSession session) {
        boolean restricted = Boolean.parseBoolean(String.valueOf(
                session.get(TenantConstants.SESSION_STORE_RESTRICTED)));
        if (!restricted) {
            return List.of();
        }
        Object activeStoreId = session.get(TenantConstants.SESSION_ACTIVE_STORE_ID);
        if (activeStoreId != null) {
            return List.of(parseId(activeStoreId.toString()));
        }
        // 兼容升级前已存在的会话；新会话始终写入 activeStoreId。
        List<Long> legacy = parseStoreIds(session.get(TenantConstants.SESSION_STORE_IDS));
        return legacy.size() == 1 ? legacy : List.of();
    }

    private void establishScope(Long tenantId, boolean superAdmin, boolean restricted, List<Long> storeIds) {
        TenantContext.setTenantId(tenantId);
        TenantContext.setSuperAdmin(superAdmin);
        StoreContext.setRestricted(!superAdmin && restricted, storeIds);
        // 进入指定商户的超管也必须受 tenant_id 过滤，只在平台全局模式旁路。
        if (superAdmin && tenantId == null) {
            TenantIgnore.open();
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        clearScope();
    }

    /** SSE/异步 MVC 会提前释放请求线程，必须立即清理 ThreadLocal 租户范围。 */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        clearScope();
    }

    private void clearScope() {
        TenantIgnore.suspend();
        TenantContext.clear();
        StoreContext.clear();
    }

    private boolean hasInternalHeaders(HttpServletRequest request) {
        return request.getHeader(TenantConstants.HEADER_TENANT_ID) != null
                || request.getHeader(TenantConstants.HEADER_SUPER_ADMIN) != null
                || request.getHeader(TenantConstants.HEADER_STORE_RESTRICTED) != null
                || request.getHeader(TenantConstants.HEADER_STORE_IDS) != null
                || request.getHeader(TenantConstants.HEADER_INTERNAL_CALL) != null;
    }

    private void verifyInternalCall(String suppliedSecret) {
        if (!hasText(internalCallSecret) || !hasText(suppliedSecret)
                || !MessageDigest.isEqual(internalCallSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("非法的内部调用凭据");
        }
    }

    private void requireActiveTenant(Long tenantId) {
        if (!tenantAccessValidator.isActive(tenantId)) {
            throw new SecurityException("商户不存在、已停用、已到期或未分配有效套餐");
        }
    }

    private Long parseId(String value) {
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new IllegalArgumentException("租户或门店ID必须为正数");
            }
            return id;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("租户或门店ID格式错误", error);
        }
    }

    private List<Long> parseStoreIds(Object value) {
        if (value == null || value.toString().isBlank()) {
            return List.of();
        }
        if (value instanceof Collection<?> values) {
            return values.stream().map(item -> parseId(String.valueOf(item))).distinct().toList();
        }
        return Arrays.stream(value.toString().split(",")).map(this::parseId).distinct().toList();
    }

    private boolean isLoginSafely() {
        try {
            return StpUtil.isLogin();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
