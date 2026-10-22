package org.hongtu.job;

import jakarta.annotation.Resource;
import org.hongtu.service.LotteryCouponReceiptNotifier;
import org.hongtu.utils.RedisJobLease;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 重试抽奖服务尚未确认的已提交发券事件。 */
@Component
@DisallowConcurrentExecution
public class LotteryCouponReceiptJob implements Job {
    @Resource private LotteryCouponReceiptNotifier notifier;
    @Resource private RedisJobLease lease;

    @Override
    public void execute(JobExecutionContext context) {
        lease.runIfAcquired("lottery-coupon-receipt", Duration.ofMinutes(5), notifier::reconcile);
    }
}
