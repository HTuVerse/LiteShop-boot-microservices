package org.hongtu.saas.service;

import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.tenant.TenantContext;
import org.hongtu.utils.RedisJobLease;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;

/** 回收 AI 服务异常退出后超过模型超时时间的配额预占。 */
@Component
public class SaasAiGenerationRecoveryJob {
    private final SaasAiGenerationService generations;
    private final RedisJobLease redisJobLease;

    public SaasAiGenerationRecoveryJob(SaasAiGenerationService generations,
                                       RedisJobLease redisJobLease) {
        this.generations = generations;
        this.redisJobLease = redisJobLease;
    }

    @Scheduled(fixedDelay = 60000)
    public void recover() {
        redisJobLease.runIfAcquired("saas-ai-generation-recovery", Duration.ofMinutes(10), this::recoverStale);
    }

    /**
     * 回收超时的 AI 配额预占；租约只负责多实例互斥，单条状态仍由数据库事务保证幂等。
     */
    private void recoverStale() {
        try {
            // 单次模型最长 60 秒并可重试，完整诊断还包含顺序子任务；
            // 因此给予 10 分钟租期，避免把仍在正常生成的记录误判为失败。
            var stale = generations.stale(LocalDateTime.now().minusMinutes(10));
            for (var row : stale) {
                try {
                    TenantContext.setTenantId(row.getTenantId());
                    generations.fail(new AiGenerationDTO().setRequestId(row.getRequestId()));
                } catch (RuntimeException ignored) {
                    // 单条失败留待下次重试，避免阻塞其他租户的配额回收。
                } finally {
                    TenantContext.setTenantId(null);
                }
            }
        } finally {
            TenantContext.clear();
        }
    }
}
