package org.hongtu.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.service.CouponService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.hongtu.utils.RedisJobLease;

import java.time.Duration;

/**
 * 过期优惠券自动下架定时任务
 * 将已过期且仍为上架状态的优惠券自动下架
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@Component
@DisallowConcurrentExecution  // 禁止并发执行
public class CouponAutoOffShelfJob extends QuartzJobBean {

    @Resource
    private CouponService couponService;

    @Resource
    private TenantJobExecutor tenantJobExecutor;

    @Resource
    private RedisJobLease redisJobLease;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            boolean executed = redisJobLease.runIfAcquired(
                    "coupon-auto-off-shelf", Duration.ofMinutes(30), () -> {
                        long startTime = System.currentTimeMillis();
                        int updatedCount = tenantJobExecutor.executeForActiveTenants(
                                "优惠券自动下架", couponService::batchOffShelfExpiredCoupons);
                        log.info("[Quartz] 过期优惠券自动下架完成，下架数量：{}，耗时：{}ms",
                                updatedCount, System.currentTimeMillis() - startTime);
                    });
            if (!executed) {
                log.debug("[Quartz] 其他实例正在执行优惠券自动下架，本轮跳过");
            }
        } catch (Exception e) {
            log.error("[Quartz] 过期优惠券自动下架任务执行异常", e);
            throw new JobExecutionException("过期优惠券自动下架任务执行失败", e);
        }
    }
}
