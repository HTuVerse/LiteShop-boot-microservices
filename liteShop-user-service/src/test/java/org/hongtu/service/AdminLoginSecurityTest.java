package org.hongtu.service;

import org.hongtu.Result.Result;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.dto.WebsiteConfigDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.utils.RateLimitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminLoginSecurityTest {
    private final ConfigFeignClient client = mock(ConfigFeignClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RateLimitUtil limiter = mock(RateLimitUtil.class);
    private final AdminLoginSecurity security = new AdminLoginSecurity(client, redis, limiter);
    private final WebsiteConfigDTO config = new WebsiteConfigDTO();
    private final Supplier<Result> authenticate = mock(Supplier.class);

    @BeforeEach void setup() { when(client.website()).thenReturn(Result.success(config)); }

    @Test void cannotBypassCaptchaByCallingLoginDirectly() {
        config.setCaptchaEnabled(true);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> security.authenticate("operator", null, null, authenticate)).getCode());
        verifyNoInteractions(authenticate);
    }

    @Test void customerLoginRequiresCaptchaWhenEnabledAndConsumesItOnce() {
        config.setCaptchaEnabled(true);
        ValueOperations values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn("ABCD", (String) null);
        String id = "0123456789abcdef0123456789abcdef";

        assertEquals(400, assertThrows(BusinessException.class,
                () -> security.verifyCustomerCaptcha(null, null)).getCode());
        assertDoesNotThrow(() -> security.verifyCustomerCaptcha(id, "abcd"));
        assertEquals(400, assertThrows(BusinessException.class,
                () -> security.verifyCustomerCaptcha(id, "abcd")).getCode());
        verify(values, times(2)).getAndDelete(anyString());
    }

    @Test void customerLoginSkipsCaptchaWhenDisabled() {
        assertDoesNotThrow(() -> security.verifyCustomerCaptcha(null, null));
        verifyNoInteractions(redis);
    }

    @Test void captchaIsConsumedBeforeComparisonAndCannotBeReplayed() {
        config.setCaptchaEnabled(true);
        ValueOperations values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn("ABCD", (String) null);
        when(authenticate.get()).thenReturn(Result.success());
        String id = "0123456789abcdef0123456789abcdef";
        assertEquals(200, security.authenticate("operator", id, "abcd", authenticate).getCode());
        assertThrows(BusinessException.class, () -> security.authenticate("operator", id, "abcd", authenticate));
        verify(authenticate, times(1)).get();
    }

    @Test void lockedAccountCannotReachCredentialVerification() {
        config.setMaxLoginAttempts(3);
        when(redis.hasKey(endsWith(":locked"))).thenReturn(true);
        assertEquals(429, assertThrows(BusinessException.class,
                () -> security.authenticate("operator", null, null, authenticate)).getCode());
        verifyNoInteractions(authenticate);
    }

    @Test void reachingFailureThresholdReportsLockAndSuccessClearsFailures() {
        config.setMaxLoginAttempts(3);
        when(authenticate.get()).thenReturn(Result.error("wrong password"), Result.success());
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        assertEquals(429, security.authenticate("operator", null, null, authenticate).getCode());
        assertEquals(200, security.authenticate("operator", null, null, authenticate).getCode());
        verify(redis).delete(endsWith(":failures"));
    }

    @Test void maintenanceAndUnavailablePolicyFailClosed() {
        config.setMaintenanceEnabled(true);
        assertEquals(503, assertThrows(BusinessException.class, security::requireOpenForMerchant).getCode());
        when(client.website()).thenThrow(new IllegalStateException("offline"));
        assertEquals(503, assertThrows(BusinessException.class,
                () -> security.authenticate("operator", null, null, authenticate)).getCode());
        verifyNoInteractions(authenticate);
    }

    @Test void customerEndpointsCannotBypassWebsiteSecurityForAdminAccounts() {
        config.setCaptchaEnabled(true);
        assertDoesNotThrow(() -> security.requireCustomerLoginAllowed(java.util.List.of("USER")));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> security.requireCustomerLoginAllowed(java.util.List.of("ADMIN"))).getCode());
    }
}
