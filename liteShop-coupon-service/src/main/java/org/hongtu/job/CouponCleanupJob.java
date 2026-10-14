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
 * 过期优惠券清理/统计定时任务
 * 统计超过指定天数的过期优惠券数量（可扩展为归档或清理）
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@Component
@DisallowConcurrentExecution  // 禁止并发执行
public class CouponCleanupJob extends QuartzJobBean {

    @Resource
    private UserCouponService userCouponService;

    @Resource
    private TenantJobExecutor tenantJobExecutor;

    @Resource
    private RedisJobLease redisJobLease;

    /**
     * 过期天数阈值（超过该天数的优惠券将被统计/清理）
     */
    private static final int EXPIRED_DAYS_THRESHOLD = 90;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            boolean executed = redisJobLease.runIfAcquired(
                    "coupon-expired-statistics", Duration.ofMinutes(30), () -> {
                        long startTime = System.currentTimeMillis();
                        // 当前任务只统计，不执行物理删除，归档策略需独立评审后启用。
                        int expiredCount = tenantJobExecutor.executeForActiveTenants(
                                "过期优惠券统计",
                                () -> userCouponService.countLongExpiredCoupons(EXPIRED_DAYS_THRESHOLD));
                        log.info("[Quartz] 过期优惠券统计完成，超过{}天数量：{}，耗时：{}ms",
                                EXPIRED_DAYS_THRESHOLD, expiredCount, System.currentTimeMillis() - startTime);
                    });
            if (!executed) {
                log.debug("[Quartz] 其他实例正在执行过期优惠券统计，本轮跳过");
            }
        } catch (Exception e) {
            log.error("[Quartz] 过期优惠券清理/统计任务执行异常", e);
            throw new JobExecutionException("过期优惠券清理/统计任务执行失败", e);
        }
    }
}
