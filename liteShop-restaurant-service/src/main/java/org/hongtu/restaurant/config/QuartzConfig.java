package org.hongtu.restaurant.config;

import org.hongtu.restaurant.job.LocalOutboxReconcileJob;
import org.hongtu.restaurant.job.OrderTimeoutJob;
import org.hongtu.restaurant.job.PendingPaymentReconcileJob;
import org.hongtu.restaurant.job.StoreCountBaselineJob;
import org.hongtu.restaurant.job.PrintJobDispatchJob;
import org.hongtu.restaurant.job.MenuChangeEffectiveJob;
import org.hongtu.restaurant.job.TenantAnalysisRefreshJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz 定时任务配置 - 餐厅订单模块
 * 配置订单超时自动取消定时任务
 *
 * @author Ht
 * @since 2025-02-06
 */
@Configuration("restaurantQuartzConfig")
public class QuartzConfig {

    /** 启动后及之后每小时同步存量门店基线。 */
    @Bean
    public JobDetail storeCountBaselineJobDetail() {
        return JobBuilder.newJob(StoreCountBaselineJob.class)
                .withIdentity("storeCountBaselineJob", "restaurantGroup").storeDurably().build();
    }

    @Bean
    public Trigger storeCountBaselineTrigger(JobDetail storeCountBaselineJobDetail) {
        return TriggerBuilder.newTrigger().forJob(storeCountBaselineJobDetail)
                .withIdentity("storeCountBaselineTrigger", "restaurantGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(1).repeatForever()).build();
    }

    /** 回调丢失后的渠道补查任务。 */
    @Bean
    public JobDetail pendingPaymentReconcileJobDetail() {
        return JobBuilder.newJob(PendingPaymentReconcileJob.class)
                .withIdentity("pendingPaymentReconcileJob", "orderGroup")
                .storeDurably()
                .build();
    }

    /** 每30秒轮流核对未支付、支付失败和对账中的线上订单。 */
    @Bean
    public Trigger pendingPaymentReconcileTrigger(JobDetail pendingPaymentReconcileJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(pendingPaymentReconcileJobDetail)
                .withIdentity("pendingPaymentReconcileTrigger", "orderGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(30).repeatForever())
                .build();
    }

    /** 租户经营分析日汇总任务定义。 */
    @Bean
    public JobDetail tenantAnalysisRefreshJobDetail() {
        return JobBuilder.newJob(TenantAnalysisRefreshJob.class)
                .withIdentity("tenantAnalysisRefreshJob", "restaurantAnalysisGroup")
                .withDescription("租户经营分析脏数据队列刷新任务")
                .storeDurably()
                .build();
    }

    /** 高频轮询轻量脏数据队列；没有业务变更时不会扫描订单表。 */
    @Bean
    public Trigger tenantAnalysisRefreshJobTrigger(JobDetail tenantAnalysisRefreshJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(tenantAnalysisRefreshJobDetail)
                .withIdentity("tenantAnalysisRefreshTrigger", "restaurantAnalysisGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(5).repeatForever())
                .build();
    }

    /** 菜单变更定时生效任务定义。 */
    @Bean
    public JobDetail menuChangeEffectiveJobDetail() {
        return JobBuilder.newJob(MenuChangeEffectiveJob.class)
                .withIdentity("menuChangeEffectiveJob", "restaurantMenuGroup")
                .withDescription("菜单变更定时生效任务")
                .storeDurably()
                .build();
    }

    /** 每30秒扫描一次待生效菜单变更。 */
    @Bean
    public Trigger menuChangeEffectiveJobTrigger(JobDetail menuChangeEffectiveJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(menuChangeEffectiveJobDetail)
                .withIdentity("menuChangeEffectiveTrigger", "restaurantMenuGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(30).repeatForever())
                .build();
    }

    /**
     * 订单超时检查任务 - JobDetail
     * 定义任务的基本信息
     */
    @Bean
    public JobDetail orderTimeoutJobDetail() {
        return JobBuilder.newJob(OrderTimeoutJob.class)
                .withIdentity("orderTimeoutJob", "orderGroup")
                .withDescription("订单超时自动取消任务")
                .storeDurably()  // 持久化存储，即使没有触发器也保留
                .build();
    }

    /**
     * 订单超时检查任务 - Trigger
     * 定义任务的执行频率：每5秒执行一次
     * 
     * 执行频率说明：
     * - 5秒间隔可以保证订单超时后最多5秒内被处理
     * - 对于30分钟超时的订单，5秒的延迟误差可以接受
     * - 如果订单量很大，可以适当增加间隔（如10秒、30秒）
     */
    @Bean
    public Trigger orderTimeoutJobTrigger(JobDetail orderTimeoutJobDetail) {
        // 简单触发器：每5秒执行一次
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(5)  // 每5秒执行一次
                .repeatForever();          // 永久重复

        return TriggerBuilder.newTrigger()
                .forJob(orderTimeoutJobDetail)
                .withIdentity("orderTimeoutTrigger", "orderGroup")
                .withDescription("订单超时检查触发器 - 每5秒执行")
                .withSchedule(scheduleBuilder)
                .build();
    }

    @Bean
    public JobDetail localOutboxReconcileJobDetail() {
        return JobBuilder.newJob(LocalOutboxReconcileJob.class)
                .withIdentity("localOutboxReconcileJob", "orderGroup")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger localOutboxReconcileTrigger(JobDetail localOutboxReconcileJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(localOutboxReconcileJobDetail)
                .withIdentity("localOutboxReconcileTrigger", "orderGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(30).repeatForever())
                .build();
    }

    @Bean
    public JobDetail printJobDispatchJobDetail() {
        return JobBuilder.newJob(PrintJobDispatchJob.class)
                .withIdentity("printJobDispatchJob", "orderGroup")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger printJobDispatchTrigger(JobDetail printJobDispatchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(printJobDispatchJobDetail)
                .withIdentity("printJobDispatchTrigger", "orderGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(5).repeatForever())
                .build();
    }

    /**
     * 备用方案：使用 Cron 表达式（如果需要更灵活的调度）
     * 例如：每分钟执行一次 "0 * * * * ?"
     * 取消注释下面的方法，并注释掉上面的 Trigger
     */
    /*
    @Bean
    public Trigger orderTimeoutCronTrigger(JobDetail orderTimeoutJobDetail) {
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                .cronSchedule("0/5 * * * * ?");  // 每5秒执行一次
        
        return TriggerBuilder.newTrigger()
                .forJob(orderTimeoutJobDetail)
                .withIdentity("orderTimeoutCronTrigger", "orderGroup")
                .withDescription("订单超时检查触发器 - Cron模式")
                .withSchedule(scheduleBuilder)
                .build();
    }
    */
}
