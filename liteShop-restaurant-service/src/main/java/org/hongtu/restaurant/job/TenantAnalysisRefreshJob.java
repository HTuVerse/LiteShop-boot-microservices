package org.hongtu.restaurant.job;

import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.service.TenantAnalysisAggregationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.hongtu.utils.RedisJobLease;

import java.time.Duration;

/**
 * 租户经营分析脏数据队列消费任务。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class TenantAnalysisRefreshJob implements Job {

    private final TenantAnalysisAggregationService aggregationService;
    private final RedisJobLease redisJobLease;

    public TenantAnalysisRefreshJob(TenantAnalysisAggregationService aggregationService,
                                    RedisJobLease redisJobLease) {
        this.aggregationService = aggregationService;
        this.redisJobLease = redisJobLease;
    }

    /** 每次最多处理1000个受影响的“租户-门店-营业日”，空队列不扫描订单。 */
    @Override
    public void execute(JobExecutionContext context) {
        try {
            boolean executed = redisJobLease.runIfAcquired(
                    "restaurant-analysis-refresh", Duration.ofMinutes(30), () -> {
                        int processed = 0;
                        while (processed < 1000) {
                            int batchCount = aggregationService.rebuildDirtyBatch(100);
                            processed += batchCount;
                            if (batchCount < 100) break;
                        }
                        if (processed > 0) {
                            log.info("[租户经营分析] 已刷新{}个门店营业日", processed);
                        }
                    });
            if (!executed) {
                log.debug("[租户经营分析] 其他实例正在刷新，本轮跳过");
            }
        } catch (Exception error) {
            log.error("[租户经营分析] 脏数据队列刷新失败", error);
        }
    }
}
