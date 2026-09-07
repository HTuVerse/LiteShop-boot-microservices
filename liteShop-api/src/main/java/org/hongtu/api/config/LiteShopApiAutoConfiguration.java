package org.hongtu.api.config;

import cn.dev33.satoken.stp.StpInterface;
import feign.RequestInterceptor;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantAccessValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

/**
 * liteShop-api 自动配置
 * 引入本模块的服务自动获得：
 * 1. Feign token 透传拦截器
 * 2. 远程 StpInterface（本地已有实现的服务如 user-service 自动让位）
 */
@AutoConfiguration
public class LiteShopApiAutoConfiguration {

    @Bean
    public RequestInterceptor feignTokenRelayInterceptor(
            @Value("${liteshop.internal-call.secret:}") String internalCallSecret) {
        return new FeignTokenRelayInterceptor(internalCallSecret);
    }

    @Bean
    @ConditionalOnBean(UserFeignClient.class)
    @ConditionalOnMissingBean(StpInterface.class)
    public StpInterface remoteStpInterface(UserFeignClient userFeignClient) {
        return new RemoteStpInterface(userFeignClient);
    }

    @Bean
    @ConditionalOnBean(UserFeignClient.class)
    @ConditionalOnMissingBean(TenantAccessValidator.class)
    public TenantAccessValidator remoteTenantAccessValidator(UserFeignClient userFeignClient) {
        return tenantId -> Boolean.TRUE.equals(
                FeignResultUtil.unwrap(userFeignClient.isTenantActive(tenantId)));
    }
}
