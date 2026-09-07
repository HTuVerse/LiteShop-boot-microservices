package org.hongtu.api.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.hongtu.tenant.StoreContext;
import org.hongtu.tenant.TenantConstants;
import org.hongtu.tenant.TenantContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;

/** 只从服务器上下文重新生成内部租户、门店头；不转发客户端伪造的上下文。 */
public class FeignTokenRelayInterceptor implements RequestInterceptor {
    private static final String TOKEN_HEADER = "satoken";
    private final String internalCallSecret;

    public FeignTokenRelayInterceptor(String internalCallSecret) {
        this.internalCallSecret = internalCallSecret == null ? "" : internalCallSecret;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (internalCallSecret.isBlank()) {
            throw new IllegalStateException("未配置 liteshop.internal-call.secret");
        }
        for (String header : new String[]{TenantConstants.HEADER_TENANT_ID,
                TenantConstants.HEADER_SUPER_ADMIN, TenantConstants.HEADER_STORE_RESTRICTED,
                TenantConstants.HEADER_STORE_IDS, TenantConstants.HEADER_INTERNAL_CALL}) {
            template.removeHeader(header);
        }
        // 即使平台或定时任务没有租户，也必须为 /inner/** 提供内部调用凭据。
        template.header(TenantConstants.HEADER_INTERNAL_CALL, internalCallSecret);
        Long tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId != null) {
            template.header(TenantConstants.HEADER_TENANT_ID, tenantId.toString());
        }
        template.header(TenantConstants.HEADER_SUPER_ADMIN, Boolean.toString(TenantContext.isSuperAdmin()));
        template.header(TenantConstants.HEADER_STORE_RESTRICTED, Boolean.toString(StoreContext.isRestricted()));
        if (StoreContext.isRestricted() && !StoreContext.getStoreIds().isEmpty()) {
            template.header(TenantConstants.HEADER_STORE_IDS,
                    StoreContext.getStoreIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        }

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader(TOKEN_HEADER);
            if (token != null && !token.isBlank()) {
                template.header(TOKEN_HEADER, token);
            }
        }
    }
}