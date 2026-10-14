package org.hongtu.utils;

import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 限流失败关闭和键隔离测试。
 */
class RateLimitUtilTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void redisFailureDeniesLimitAndIdempotentChecks() {
        RateLimitUtil util = new RateLimitUtil();
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        doThrow(new RuntimeException("redis down")).when(redis).execute(any(), anyList(), any());
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new RuntimeException("redis down")).when(values)
                .setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class));
        ReflectionTestUtils.setField(util, "redisTemplate", redis);

        assertFalse(util.isAllowed("g:login", 5, 60));
        assertFalse(util.isUnderLimit("g:captcha", 5, 60));
        assertFalse(util.setIdempotentMark("g:submit", 30));
    }

    @Test
    void tenantKeyIncludesTenantAndGlobalKeyDoesNot() {
        TenantContext.setTenantId(1001L);

        String tenantKey = RateLimitUtil.tenantKey("drawLottery", "12");
        String globalKey = RateLimitUtil.globalKey("admin-login", "alice");

        assertTrue(tenantKey.contains("1001"));
        assertTrue(tenantKey.contains("drawLottery"));
        assertFalse(globalKey.contains("1001"));
        assertTrue(globalKey.startsWith("g:"));
    }

    @Test
    void missingTenantUsesFixedSegment() {
        String tenantKey = RateLimitUtil.tenantKey("drawLottery", "12");

        assertTrue(tenantKey.contains("none"));
        assertFalse(tenantKey.contains("1001"));
    }
}
