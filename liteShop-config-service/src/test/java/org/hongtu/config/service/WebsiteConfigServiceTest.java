package org.hongtu.config.service;

import jakarta.validation.Validation;
import org.hongtu.api.dto.WebsiteConfigDTO;
import org.hongtu.config.DAO.WebsiteConfigMapper;
import org.hongtu.config.controller.WebsiteConfigController;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebsiteConfigServiceTest {
    private final WebsiteConfigMapper mapper = mock(WebsiteConfigMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final WebsiteConfigService service = new WebsiteConfigService(mapper, redis);

    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void publishedReadsUseSharedCacheWithoutDatabaseQuery() {
        ValueOperations values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(WebsiteConfigService.CACHE_KEY)).thenReturn("{\"systemName\":\"Cached brand\",\"revision\":3}");
        assertEquals("Cached brand", service.getPublished().getSystemName());
        verifyNoInteractions(mapper);
    }

    @Test void staleEditsAreRejectedAndNeverTouchCache() {
        when(mapper.publish(anyString(), eq(0L), eq(7L))).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.publish(new WebsiteConfigDTO(), 7));
        assertEquals(409, ex.getCode());
        verifyNoInteractions(redis);
    }

    @Test void cacheIsPublishedOnlyAfterSuccessfulTransactionCommit() {
        when(mapper.publish(anyString(), eq(0L), eq(7L))).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        WebsiteConfigDTO submitted = new WebsiteConfigDTO();
        WebsiteConfigDTO saved = service.publish(submitted, 7);
        assertEquals(1L, saved.getRevision());
        assertEquals(0L, submitted.getRevision());
        verifyNoInteractions(redis);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(), any());
    }

    @Test void publicEndpointDoesNotExposeFailureThresholds() {
        WebsiteConfigDTO c = new WebsiteConfigDTO();
        c.setMaxLoginAttempts(3);
        var publicData = WebsiteConfigController.publicFields(c);
        assertFalse(publicData.containsKey("maxLoginAttempts"));
        assertFalse(publicData.containsKey("lockMinutes"));
        assertEquals("LiteShop", publicData.get("systemName"));
    }

    @Test void invalidSecurityAndConditionalFieldsAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            WebsiteConfigDTO c = new WebsiteConfigDTO();
            assertTrue(validator.validate(c).isEmpty());
            c.setSystemName("  "); c.setDefaultLanguage("unsupported"); c.setMaxLoginAttempts(0);
            c.setWatermarkEnabled(true); c.setWatermarkMode("custom");
            c.setMaintenanceEnabled(true); c.setMaintenanceMessage("");
            assertTrue(validator.validate(c).size() >= 5);
        }
    }
}
