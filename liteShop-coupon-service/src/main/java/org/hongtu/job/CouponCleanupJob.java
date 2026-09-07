package org.hongtu.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.service.UserCouponService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

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

    /**
     * 过期天数阈值（超过该天数的优惠券将被统计/清理）
     */
    private static final int EXPIRED_DAYS_THRESHOLD = 90;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("========== [Quartz] 开始执行过期优惠券清理/统计任务 ==========");
        long startTime = System.currentTimeMillis();
        try {
            // 统计过期超过90天的优惠券数量（暂不删除，只记录日志）
            int expiredCount = tenantJobExecutor.executeForActiveTenants(
                    "过期优惠券统计",
                    () -> userCouponService.countLongExpiredCoupons(EXPIRED_DAYS_THRESHOLD));
            long costTime = System.currentTimeMillis() - startTime;
            log.info("[Quartz] 过期优惠券清理/统计任务执行完成，超过{}天的过期优惠券数量：{}，耗时：{}ms",
                    EXPIRED_DAYS_THRESHOLD, expiredCount, costTime);

            // 如果需要实际清理，可以在这里添加删除或归档逻辑
            // if (expiredCount > 0) {
            //     userCouponService.archiveLongExpiredCoupons(EXPIRED_DAYS_THRESHOLD);
            // }
        } catch (Exception e) {
            log.error("[Quartz] 过期优惠券清理/统计任务执行异常", e);
            throw new JobExecutionException("过期优惠券清理/统计任务执行失败", e);
        }
        log.info("========== [Quartz] 过期优惠券清理/统计任务结束 ==========");
    }
}
