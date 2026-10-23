package org.hongtu.config;

import org.hongtu.job.CouponAutoOffShelfJob;
import org.hongtu.job.CouponCleanupJob;
import org.hongtu.job.CouponRevokeJob;
import org.hongtu.job.CouponStatusUpdateJob;
import org.hongtu.job.LotteryCouponReceiptJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz 定时任务配置 - 优惠券模块
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Configuration("couponQuartzConfig")
public class QuartzConfig {

    /** 未完成的模板回收每分钟推进一批。 */
    @Bean
    public JobDetail couponRevokeJobDetail() {
        return JobBuilder.newJob(CouponRevokeJob.class)
                .withIdentity("couponRevokeJob", "couponGroup").storeDurably().build();
    }

    @Bean
    public Trigger couponRevokeTrigger() {
        return TriggerBuilder.newTrigger().forJob(couponRevokeJobDetail())
                .withIdentity("couponRevokeTrigger", "couponGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(1).repeatForever()).build();
    }

    /** 已提交发券事件的抽奖领取通知，每 30 秒对账一次。 */
    @Bean
    public JobDetail lotteryCouponReceiptJobDetail() {
        return JobBuilder.newJob(LotteryCouponReceiptJob.class)
                .withIdentity("lotteryCouponReceiptJob", "couponGroup")
                .storeDurably().build();
    }

    @Bean
    public Trigger lotteryCouponReceiptTrigger() {
        return TriggerBuilder.newTrigger().forJob(lotteryCouponReceiptJobDetail())
                .withIdentity("lotteryCouponReceiptTrigger", "couponGroup")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(30).repeatForever())
                .build();
    }

    // ==================== 优惠券状态更新任务 ====================

    /**
     * 优惠券状态更新任务 - JobDetail
     */
    @Bean
    public JobDetail couponStatusUpdateJobDetail() {
        return JobBuilder.newJob(CouponStatusUpdateJob.class)
                .withIdentity("couponStatusUpdateJob", "couponGroup")
                .withDescription("优惠券状态更新任务：更新已生效和已过期的优惠券状态")
                .storeDurably()  // 即使没有触发器也保持存储
                .build();
    }

    /**
     * 优惠券状态更新任务 - Trigger
     * 每5分钟执行一次
     */
    @Bean
    public Trigger couponStatusUpdateTrigger() {
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                .cronSchedule("0 */5 * * * ?")  // 每5分钟执行
                .withMisfireHandlingInstructionDoNothing();  // 错过的任务不补执行

        return TriggerBuilder.newTrigger()
                .forJob(couponStatusUpdateJobDetail())
                .withIdentity("couponStatusUpdateTrigger", "couponGroup")
                .withDescription("每5分钟执行一次优惠券状态更新")
                .withSchedule(scheduleBuilder)
                .build();
    }

    // ==================== 过期优惠券自动下架任务 ====================

    /**
     * 过期优惠券自动下架任务 - JobDetail
     */
    @Bean
    public JobDetail couponAutoOffShelfJobDetail() {
        return JobBuilder.newJob(CouponAutoOffShelfJob.class)
                .withIdentity("couponAutoOffShelfJob", "couponGroup")
                .withDescription("过期优惠券自动下架任务：下架已过期的优惠券")
                .storeDurably()
                .build();
    }

    /**
     * 过期优惠券自动下架任务 - Trigger
     * 每小时执行一次
     */
    @Bean
    public Trigger couponAutoOffShelfTrigger() {
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                .cronSchedule("0 0 * * * ?")  // 每小时整点执行
                .withMisfireHandlingInstructionDoNothing();

        return TriggerBuilder.newTrigger()
                .forJob(couponAutoOffShelfJobDetail())
                .withIdentity("couponAutoOffShelfTrigger", "couponGroup")
                .withDescription("每小时执行一次过期优惠券自动下架")
                .withSchedule(scheduleBuilder)
                .build();
    }

    // ==================== 过期优惠券清理/统计任务 ====================

    /**
     * 过期优惠券清理/统计任务 - JobDetail
     */
    @Bean
    public JobDetail couponCleanupJobDetail() {
        return JobBuilder.newJob(CouponCleanupJob.class)
                .withIdentity("couponCleanupJob", "couponGroup")
                .withDescription("过期优惠券清理/统计任务：统计长期过期的优惠券")
                .storeDurably()
                .build();
    }

    /**
     * 过期优惠券清理/统计任务 - Trigger
     * 每天凌晨3点执行一次
     */
    @Bean
    public Trigger couponCleanupTrigger() {
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                .cronSchedule("0 0 3 * * ?")  // 每天凌晨3点执行
                .withMisfireHandlingInstructionDoNothing();

        return TriggerBuilder.newTrigger()
                .forJob(couponCleanupJobDetail())
                .withIdentity("couponCleanupTrigger", "couponGroup")
                .withDescription("每天凌晨3点执行过期优惠券清理/统计")
                .withSchedule(scheduleBuilder)
                .build();
    }
}
