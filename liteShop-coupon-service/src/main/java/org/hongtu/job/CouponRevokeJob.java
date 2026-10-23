package org.hongtu.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponRevokeBatchMapper;
import org.hongtu.service.UserCouponService;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.hongtu.utils.RedisJobLease;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 继续处理服务中断时尚未完成的模板回收任务。 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class CouponRevokeJob extends QuartzJobBean {
    @Resource private UserCouponService userCouponService;
    @Resource private CouponRevokeBatchMapper revokeBatchMapper;
    @Resource private RedisJobLease redisJobLease;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            redisJobLease.runIfAcquired("coupon-revoke-batch", Duration.ofMinutes(2), () -> {
                int count = 0;
                for (Long tenantId : TenantIgnore.get(revokeBatchMapper::pendingTenantIds)) {
                    if (tenantId == null) continue;
                    try {
                        count += TenantScope.call(tenantId, userCouponService::reconcilePendingRecalls);
                    } catch (RuntimeException e) {
                        log.error("模板回收租户任务失败，下轮继续: tenantId={}", tenantId, e);
                    }
                }
                if (count > 0) log.info("模板批量回收继续处理 {} 张用户券", count);
            });
        } catch (Exception e) {
            throw new JobExecutionException("模板回收任务执行失败", e);
        }
    }
}
