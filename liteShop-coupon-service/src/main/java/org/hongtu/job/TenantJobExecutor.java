package org.hongtu.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.IntSupplier;

/** 让平台级 Quartz 任务逐租户执行，避免跨租户全表更新。 */
@Slf4j
@Component
public class TenantJobExecutor {

    @Resource
    private UserFeignClient userFeignClient;

    public int executeForActiveTenants(String jobName, IntSupplier action) {
        Result<List<Long>> response = userFeignClient.getActiveTenantIds();
        if (response == null || !Result.CODE_SUCCESS.equals(response.getCode()) || response.getData() == null) {
            throw new IllegalStateException("无法获取有效租户列表: " + (response == null ? "无响应" : response.getMsg()));
        }

        int total = 0;
        int failed = 0;
        for (Long tenantId : response.getData()) {
            if (tenantId == null || tenantId <= 0) {
                continue;
            }
            TenantContext.setTenantId(tenantId);
            try {
                total += action.getAsInt();
            } catch (Exception e) {
                failed++;
                log.error("[{}] 租户任务执行失败: tenantId={}", jobName, tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        if (failed > 0) {
            log.warn("[{}] 有 {} 个租户执行失败，将在下次调度重试", jobName, failed);
        }
        return total;
    }
}
