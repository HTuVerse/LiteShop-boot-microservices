package org.hongtu.restaurant.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.service.MenuChangeService;
import org.quartz.Job;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.hongtu.utils.RedisJobLease;

import java.time.Duration;

/**
 * 菜单变更定时生效任务。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class MenuChangeEffectiveJob implements Job {

    @Resource
    private MenuChangeService menuChangeService;

    @Resource
    private RedisJobLease redisJobLease;

    /** 每次扫描一批已经到达生效时间的菜单变更。 */
    @Override
    public void execute(JobExecutionContext context) {
        try {
            boolean executed = redisJobLease.runIfAcquired(
                    "restaurant-menu-change-effective", Duration.ofMinutes(15),
                    menuChangeService::processDueRecords);
            if (!executed) {
                log.debug("[菜单变更任务] 其他实例正在执行，本轮跳过");
            }
        } catch (Exception error) {
            log.error("[菜单变更任务] 扫描执行失败: {}", error.getMessage(), error);
        }
    }
}
