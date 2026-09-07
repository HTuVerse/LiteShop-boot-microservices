package org.hongtu.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 删除外部请求伪造的内部租户头。
 * 可信租户头只允许由服务间 Feign 调用在网关之后写入。
 */
@Component
public class TenantHeaderSanitizingFilter implements GlobalFilter, Ordered {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String SUPER_ADMIN_HEADER = "X-Super-Admin";
    private static final String STORE_RESTRICTED_HEADER = "X-Store-Restricted";
    private static final String STORE_IDS_HEADER = "X-Store-Ids";
    private static final String INTERNAL_CALL_HEADER = "X-Internal-Call";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(TENANT_HEADER);
                    headers.remove(SUPER_ADMIN_HEADER);
                    headers.remove(STORE_RESTRICTED_HEADER);
                    headers.remove(STORE_IDS_HEADER);
                    headers.remove(INTERNAL_CALL_HEADER);
                }))
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
