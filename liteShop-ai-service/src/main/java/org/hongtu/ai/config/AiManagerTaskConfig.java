package org.hongtu.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** AI 店长后台分析任务执行器。 */
@Configuration
public class AiManagerTaskConfig {

    /**
     * 使用独立小线程池，避免模型调用占满 Web 请求线程。
     * 队列满时拒绝新任务，由提交方返回繁忙；停机时等待在途任务结算额度。
     */
    @Bean(name = "aiManagerTaskExecutor")
    public ThreadPoolTaskExecutor aiManagerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-manager-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 单次模型超时 60 秒且最多重试 2 次，简报和菜品顺序执行，尽量等额度结算完成。
        executor.setAwaitTerminationSeconds(180);
        return executor;
    }
}
