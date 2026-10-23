package org.hongtu.saas.usage;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hongtu.saas.constant.SaasFeatureKeys;
import org.hongtu.saas.domain.SaasUsageCounter;
import org.hongtu.saas.mapper.SaasUsageCounterMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class StoreCountUsageProvider implements SaasUsageProvider {
    private final SaasUsageCounterMapper counters;

    @Override public String metric() { return "store_count"; }

    @Override
    public BigDecimal currentUsage(Long tenantId) {
        SaasUsageCounter counter = counters.selectOne(new LambdaQueryWrapper<SaasUsageCounter>()
                .eq(SaasUsageCounter::getTenantId, tenantId)
                .eq(SaasUsageCounter::getFeatureCode, SaasFeatureKeys.STORE_COUNT)
                .eq(SaasUsageCounter::getPeriodStart, LocalDate.of(1970, 1, 1).atStartOfDay()));
        // 未初始化的存量租户必须先由门店服务推送基线，不能当作 0 放行。
        return counter == null ? null : counter.getUsedValue();
    }
}
