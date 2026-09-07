package org.hongtu.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.service.CouponService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

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

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("========== [Quartz] 开始执行过期优惠券自动下架任务 ==========");
        long startTime = System.currentTimeMillis();
        try {
            int updatedCount = tenantJobExecutor.executeForActiveTenants(
                    "优惠券自动下架", couponService::batchOffShelfExpiredCoupons);
            long costTime = System.currentTimeMillis() - startTime;
            log.info("[Quartz] 过期优惠券自动下架任务执行完成，下架数量：{}，耗时：{}ms", updatedCount, costTime);
        } catch (Exception e) {
            log.error("[Quartz] 过期优惠券自动下架任务执行异常", e);
            throw new JobExecutionException("过期优惠券自动下架任务执行失败", e);
        }
        log.info("========== [Quartz] 过期优惠券自动下架任务结束 ==========");
    }
}
