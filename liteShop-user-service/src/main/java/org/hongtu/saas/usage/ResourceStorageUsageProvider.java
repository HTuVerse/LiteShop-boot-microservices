package org.hongtu.saas.usage;

import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.FileResourceClient;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 从文件服务实时读取租户全部资源占用容量。 */
@Component
@RequiredArgsConstructor
public class ResourceStorageUsageProvider implements SaasUsageProvider {
    private final FileResourceClient files;

    @Override public String metric() { return SaasFeatureCode.RESOURCE_STORAGE_BYTES; }

    @Override
    public BigDecimal currentUsage(Long tenantId) {
        Long used = TenantScope.call(tenantId, () -> FeignResultUtil.unwrap(files.usedBytes()));
        return BigDecimal.valueOf(used == null ? 0 : used);
    }
}
