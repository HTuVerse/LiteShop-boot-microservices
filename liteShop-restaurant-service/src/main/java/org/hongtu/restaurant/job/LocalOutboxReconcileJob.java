package org.hongtu.restaurant.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.service.LocalOutboxService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * 对账跨服务发件箱。远程已成功但本地订单回滚时执行补偿。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class LocalOutboxReconcileJob implements Job {

    @Resource
    private LocalOutboxService localOutboxService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            localOutboxService.reconcile();
        } catch (Exception exception) {
            log.error("发件箱对账失败", exception);
        }
    }
}
