package org.hongtu.restaurant.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.DTO.TenantStoreCount;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.hongtu.utils.RedisJobLease;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 将门店库的存量计数单向推送到用户库，避免权益查询反向调用门店服务。 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class StoreCountBaselineJob implements Job {
    @Resource private StoreInfoMapper storeInfoMapper;
    @Resource private UserFeignClient userFeignClient;
    @Resource private RedisJobLease redisJobLease;

    @Override
    public void execute(JobExecutionContext context) {
        redisJobLease.runIfAcquired("restaurant-store-count-baseline", Duration.ofMinutes(10), () -> {
            long cursor = 0;
            while (true) {
                long after = cursor;
                List<TenantStoreCount> rows = TenantIgnore.get(() -> storeInfoMapper.countByTenantAfter(after));
                if (rows.isEmpty()) return;
                for (TenantStoreCount row : rows) {
                    cursor = row.getTenantId();
                    try {
                        TenantScope.run(row.getTenantId(), () -> {
                            Result<Void> response = userFeignClient.ensureStoreCountBaseline(row.getStoreCount());
                            if (response == null || !Result.CODE_SUCCESS.equals(response.getCode()))
                                throw new IllegalStateException("门店基线同步失败");
                        });
                    } catch (RuntimeException e) {
                        log.error("门店用量基线同步失败，下轮重试: tenantId={}", row.getTenantId(), e);
                    }
                }
                if (rows.size() < 100) return;
            }
        });
    }
}
