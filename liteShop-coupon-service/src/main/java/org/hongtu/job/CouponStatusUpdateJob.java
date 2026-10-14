package org.hongtu.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.service.UserCouponService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.hongtu.utils.RedisJobLease;

import java.time.Duration;

/**
 * 优惠券状态更新定时任务
 * 1. 将已过期的优惠券状态更新为"已过期"
 * 2. 将已生效的优惠券状态更新为"未使用"
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@Component
@DisallowConcurrentExecution  // 禁止并发执行，确保上一次任务完成后才执行下一次
public class CouponStatusUpdateJob extends QuartzJobBean {

    @Resource
    private UserCouponService userCouponService;

    @Resource
    private TenantJobExecutor tenantJobExecutor;

    @Resource
    private RedisJobLease redisJobLease;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            boolean executed = redisJobLease.runIfAcquired(
                    "coupon-status-update", Duration.ofMinutes(30), () -> {
                        long startTime = System.currentTimeMillis();
                        int updatedCount = tenantJobExecutor.executeForActiveTenants(
                                "优惠券状态更新", userCouponService::batchUpdateExpiredCoupons);
                        log.info("[Quartz] 优惠券状态更新完成，更新数量：{}，耗时：{}ms",
                                updatedCount, System.currentTimeMillis() - startTime);
                    });
            if (!executed) {
                log.debug("[Quartz] 其他实例正在执行优惠券状态更新，本轮跳过");
            }
        } catch (Exception e) {
            log.error("[Quartz] 优惠券状态更新任务执行异常", e);
            throw new JobExecutionException("优惠券状态更新任务执行失败", e);
        }
    }
}
