package org.hongtu.restaurant.config;

import jakarta.annotation.Resource;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Component;


/** 从配置服务动态读取指定租户的餐饮运行参数。 */
@Component
public class TenantRestaurantConfig {
    @Resource private ConfigFeignClient configFeignClient;

    /** 回调、异步任务必须传入业务实体所属租户，不能使用请求头推断。 */
    public String get(Long tenantId, String key) {
        String value = FeignResultUtil.unwrap(configFeignClient.restaurant(tenantId, key));
        return value == null ? "" : value;
    }

    public String get(String key) {
        return get(TenantContext.getTenantId(), key);
    }

    public boolean sandboxEnabled(Long tenantId) {
        return Boolean.TRUE.equals(FeignResultUtil.unwrap(configFeignClient.paymentSandbox(tenantId)));
    }

    /** 仅暴露渠道开关，顾客端不读取商户参数或密钥。 */
    public boolean paymentEnabled(Long tenantId, String channel) {
        TenantPaymentRuntimeDTO account = FeignResultUtil.unwrap(configFeignClient.paymentAccount(tenantId, channel));
        return account != null && Boolean.TRUE.equals(account.getEnabled());
    }

}
