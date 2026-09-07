package org.hongtu.config;

import jakarta.annotation.Resource;
import org.hongtu.DTO.SysTenant;
import org.hongtu.service.SysTenantService;
import org.hongtu.tenant.TenantAccessValidator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** user-service 的租户状态本地权威校验，避免调用自身 Feign 接口。 */
@Component
public class LocalTenantAccessValidator implements TenantAccessValidator {

    @Resource
    private SysTenantService tenantService;

    @Override
    public boolean isActive(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return false;
        }
        SysTenant tenant = tenantService.getById(tenantId);
        return tenant != null
                && Integer.valueOf(1).equals(tenant.getStatus())
                && (tenant.getExpireTime() == null
                || tenant.getExpireTime().isAfter(LocalDateTime.now()));
    }
}
