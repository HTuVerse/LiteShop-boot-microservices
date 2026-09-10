package org.hongtu.saas.usage;

import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.RestaurantFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class StoreCountUsageProvider implements SaasUsageProvider {
    private final RestaurantFeignClient restaurant;

    @Override public String metric() { return "store_count"; }

    @Override
    public BigDecimal currentUsage(Long tenantId) {
        Long count = TenantScope.call(tenantId, () -> FeignResultUtil.unwrap(restaurant.countStores()));
        return BigDecimal.valueOf(count == null ? 0 : count);
    }
}
