package org.hongtu.storage;

import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.FileUploadMapper;
import org.hongtu.DAO.ResourceQuotaLockMapper;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 在上传事务内串行校验租户资源库总容量。 */
@Component
@RequiredArgsConstructor
public class ResourceCapacityGuard {
    private final ResourceQuotaLockMapper locks;
    private final FileUploadMapper files;
    private final UserFeignClient users;

    /** 调用者需开启事务，行锁保持到文件记录提交。 */
    public void requireAvailable(long bytes) {
        if (bytes < 0) throw new BusinessException(400, "文件大小不合法");
        Long tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId == null) {
            if (!TenantContext.isSuperAdmin()) throw new SecurityException("缺少租户上下文");
            return;
        }
        locks.ensure(tenantId);
        locks.lock(tenantId);
        EntitlementMutationDTO request = new EntitlementMutationDTO();
        request.setFeatureCode(SaasFeatureCode.RESOURCE_STORAGE_BYTES);
        request.setCurrentUsage(BigDecimal.valueOf(files.usedBytes(tenantId)));
        request.setAmount(BigDecimal.valueOf(bytes));
        EntitlementDecisionDTO decision = FeignResultUtil.unwrap(users.checkEntitlement(request));
        if (decision == null || !decision.isAllowed())
            throw new BusinessException(409, "资源库容量不足，请联系平台分配容量或加购扩容包");
    }
}
