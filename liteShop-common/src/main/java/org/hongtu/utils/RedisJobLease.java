package org.hongtu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 多实例定时任务租约。只用于防止同一批任务并发执行，不代替业务自身的幂等约束。
 */
@Component
@Slf4j
public class RedisJobLease {

    private static final String KEY_PREFIX = "job:lease:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisJobLease(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获得租约后执行任务；其他实例持有租约时返回 false。
     */
    public boolean runIfAcquired(String jobName, Duration leaseDuration, Runnable task) {
        if (jobName == null || jobName.isBlank() || leaseDuration == null
                || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("任务名和租约时长必须有效");
        }
        String key = KEY_PREFIX + jobName;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, leaseDuration);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            try {
                redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), token);
            } catch (RuntimeException e) {
                // 释放失败时依赖 TTL 自动回收，避免覆盖任务本身的异常。
                log.warn("定时任务租约释放失败，将等待 TTL 自动回收: jobName={}", jobName, e);
            }
        }
    }
}
