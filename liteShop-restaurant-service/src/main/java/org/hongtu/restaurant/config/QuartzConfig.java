package org.hongtu.restaurant.config;

import org.hongtu.restaurant.job.OrderTimeoutJob;
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
