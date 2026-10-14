package org.hongtu.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.tenant.TenantContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的限流工具类
 * 使用滑动窗口算法实现接口限流
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Slf4j
@Component
public class RateLimitUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 限流前缀
     */
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /**
     * 幂等标记前缀
     */
    private static final String IDEMPOTENT_PREFIX = "idempotent:";

    /**
     * 没有租户上下文时的固定段，避免不同租户的用户 ID 共用一个桶。
     */
    private static final String MISSING_TENANT = "none";

    /**
     * Lua脚本：仅检查是否在限流阈值内（不增加计数）
     */
    private static final String CHECK_ONLY_SCRIPT =
            "local key = KEYS[1] "
                    + "local limit = tonumber(ARGV[1]) "
                    + "local current = tonumber(redis.call('GET', key) or '0') "
                    + "return (current < limit) and 1 or 0";

    /**
     * Lua脚本：仅增加计数并设置过期（首次时）
     */
    private static final String INCREMENT_QUOTA_SCRIPT =
            "local key = KEYS[1] "
                    + "local window = tonumber(ARGV[1]) "
                    + "redis.call('INCR', key) "
                    + "if redis.call('TTL', key) == -1 then redis.call('EXPIRE', key, window) end "
                    + "return 1";

    /**
     * Lua脚本：原子性检查并增加计数
     */
    private static final String RATE_LIMIT_SCRIPT =
            "local key = KEYS[1] " +
                    "local limit = tonumber(ARGV[1]) " +
                    "local window = tonumber(ARGV[2]) " +
                    "local current = tonumber(redis.call('GET', key) or '0') " +
                    "if current >= limit then " +
                    "    return 0 " +
                    "else " +
                    "    redis.call('INCR', key) " +
                    "    if current == 0 then " +
                    "        redis.call('EXPIRE', key, window) " +
                    "    end " +
                    "    return 1 " +
                    "end";

    /**
     * 按当前租户构造限流键。没有租户上下文时使用固定段，不和其它租户共用桶。
     */
    public static String tenantKey(String... parts) {
        Long tenantId = TenantContext.getTenantIdOrNull();
        String tenant = tenantId == null ? MISSING_TENANT : tenantId.toString();
        return join("t:" + tenant, parts);
    }

    /**
     * 构造不带请求租户的限流键。后台登录、验证码 IP 等不能按客户端租户头拆桶。
     */
    public static String globalKey(String... parts) {
        return join("g", parts);
    }

    /**
     * 检查是否允许访问（简单计数器限流）。
     * Redis 不可用时拒绝，避免验证码和登录保护失效。
     *
     * @param key       限流键，租户内场景用 {@link #tenantKey}，跨租户场景用 {@link #globalKey}
     * @param limit     限制次数
     * @param window    时间窗口（秒）
     * @return true-允许访问，false-触发限流或限流服务不可用
     */
    public boolean isAllowed(String key, int limit, int window) {
        String redisKey = RATE_LIMIT_PREFIX + key;

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(RATE_LIMIT_SCRIPT);
            script.setResultType(Long.class);

            Long result = redisTemplate.execute(script, Collections.singletonList(redisKey), limit, window);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("限流检查失败，拒绝访问，key={}", key, e);
            return false;
        }
    }

    /**
     * 检查当前租户下用户接口访问是否被限流。
     *
     * @param userId     用户ID
     * @param apiName    接口名称
     * @param limit      限制次数
     * @param windowSec  时间窗口（秒）
     * @return true-允许访问，false-触发限流或限流服务不可用
     */
    public boolean checkUserApiLimit(Long userId, String apiName, int limit, int windowSec) {
        return isAllowed(tenantKey(apiName, String.valueOf(userId)), limit, windowSec);
    }

    /**
     * 检查接口全局访问是否被限流。
     *
     * @param apiName    接口名称
     * @param limit      限制次数
     * @param windowSec  时间窗口（秒）
     * @return true-允许访问，false-触发限流或限流服务不可用
     */
    public boolean checkGlobalApiLimit(String apiName, int limit, int windowSec) {
        return isAllowed(globalKey(apiName), limit, windowSec);
    }

    /**
     * 设置幂等性标记（防止重复提交）
     *
     * @param key      幂等键
     * @param expireSec 过期时间（秒）
     * @return true-设置成功（首次请求），false-重复请求或限流服务不可用
     */
    public boolean setIdempotentMark(String key, int expireSec) {
        String redisKey = IDEMPOTENT_PREFIX + key;
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", expireSec, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.error("设置幂等性标记失败，拒绝重复提交，key={}", key, e);
            return false;
        }
    }

    /**
     * 删除幂等性标记
     *
     * @param key 幂等键
     */
    public void removeIdempotentMark(String key) {
        String redisKey = IDEMPOTENT_PREFIX + key;
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.error("删除幂等性标记失败，key={}", key, e);
        }
    }

    /**
     * 仅检查是否在限流阈值内（不消耗配额）
     * 适用于「先检查、业务成功后再调用 incrementQuota」的场景，如验证码发送：仅对发送成功次数限流。
     *
     * @param key    限流键（会加上前缀 rate_limit:）
     * @param limit  限制次数
     * @param window 时间窗口（秒），仅用于与 isAllowed 语义一致，本方法不写入 Redis
     * @return true-未超限可继续，false-已超限
     */
    public boolean isUnderLimit(String key, int limit, int window) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(CHECK_ONLY_SCRIPT);
            script.setResultType(Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(redisKey), limit);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("限流检查失败，拒绝访问，key={}", key, e);
            return false;
        }
    }

    private static String join(String scope, String... parts) {
        StringBuilder key = new StringBuilder(scope);
        if (parts == null) {
            return key.toString();
        }
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            key.append(':').append(part);
        }
        return key.toString();
    }

    /**
     * 增加一次配额计数并设置过期时间（仅首次设置）
     * 与 isUnderLimit 配合使用：先 isUnderLimit，业务成功后再调用本方法。
     *
     * @param key       限流键（与 isUnderLimit 使用同一 key）
     * @param windowSec 时间窗口（秒）
     */
    public void incrementQuota(String key, int windowSec) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(INCREMENT_QUOTA_SCRIPT);
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(redisKey), windowSec);
        } catch (Exception e) {
            log.error("增加配额计数失败，key={}", key, e);
        }
    }
}
