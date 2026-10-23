package org.hongtu.audit;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hongtu.tenant.TenantConstants;
import org.hongtu.tenant.TenantContext;
import org.hongtu.utils.IpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

/** 从可信服务端上下文建立统一审计事件。 */
@Component
@RequiredArgsConstructor
public class AuditEvents {
    private static final String REQUEST_ID_ATTRIBUTE = AuditEvents.class.getName() + ".requestId";
    private final IpUtil ipUtil;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    public AuditEvent create(String accountOverride, String module, String type, String objectType,
                             String objectId, String summary, String result, String resultMessage,
                             Long subjectTenantOverride, String safeDiffJson) {
        HttpServletRequest request = request();
        Long actorId = null;
        String account = accountOverride;
        Long scopeTenantId = TenantContext.getTenantIdOrNull();
        Long storeId = null;
        boolean superAdmin = TenantContext.isSuperAdmin();
        try {
            if (StpUtil.isLogin()) {
                actorId = StpUtil.getLoginIdAsLong();
                var session = StpUtil.getSession();
                Object snapshot = session.get(TenantConstants.SESSION_AUDIT_ACCOUNT);
                if (snapshot != null && !snapshot.toString().isBlank()) account = snapshot.toString();
                if (scopeTenantId == null) {
                    Object selectedTenant = session.get(TenantConstants.SESSION_TENANT_ID);
                    if (selectedTenant != null) scopeTenantId = Long.valueOf(selectedTenant.toString());
                }
                superAdmin = superAdmin || Boolean.parseBoolean(String.valueOf(
                        session.get(TenantConstants.SESSION_SUPER_ADMIN)));
                Object selectedStore = session.get(TenantConstants.SESSION_ACTIVE_STORE_ID);
                if (selectedStore != null) storeId = Long.valueOf(selectedStore.toString());
            }
        } catch (RuntimeException ignored) {
            // 预认证和失效会话允许只记录尝试登录的账号。
        }
        if (account == null || account.isBlank()) account = actorId == null ? "未知账号" : String.valueOf(actorId);
        Long subjectTenantId = subjectTenantOverride != null ? subjectTenantOverride : scopeTenantId;
        String requestId = requestId(request);
        String path = request == null ? "internal" : request.getRequestURI();
        String clientIp = request == null ? "unknown" : ipUtil.getClientIp(request);
        return new AuditEvent(UUID.randomUUID().toString(), Instant.now().toString(), actorId,
                limit(account, 100), superAdmin ? null : scopeTenantId, scopeTenantId,
                subjectTenantId, storeId, limit(clientIp, 45), limit(serviceName, 80),
                limit(module, 80), limit(type, 40), limit(objectType, 80), limit(objectId, 100),
                limit(summary, 500), result, limit(resultMessage, 500), requestId,
                limit(path, 300), safeDiffJson);
    }

    private HttpServletRequest request() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
            return attributes.getRequest();
        return null;
    }

    private String requestId(HttpServletRequest request) {
        if (request == null) return UUID.randomUUID().toString();
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String value) return value;
        String generated = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, generated);
        return generated;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
