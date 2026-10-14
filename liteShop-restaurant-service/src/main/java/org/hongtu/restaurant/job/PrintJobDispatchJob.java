package org.hongtu.restaurant.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.service.impl.OrderPrintServiceImpl;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * 独立发送已登记的打印任务，避免在订单事务里调用飞鹅。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class PrintJobDispatchJob implements Job {

    @Resource
    private OrderPrintServiceImpl orderPrintService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            orderPrintService.dispatchPendingJobs();
        } catch (Exception exception) {
            log.error("打印任务发送失败", exception);
        }
    }
}
