package org.hongtu.config.service.impl;

import org.hongtu.config.DTO.AppConfig;
import org.hongtu.config.service.BannerService;
import org.hongtu.exception.TenantContextMissingException;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TenantConfigCacheTest {
    @Test void stringConfigurationIsReturnedAsDataRatherThanMessage() {
        var service = mock(org.hongtu.config.service.AppConfigService.class);
        org.mockito.Mockito.when(service.getConfigValue("app_name")).thenReturn("商户甲");
        var controller = new org.hongtu.config.controller.AppConfigController(service, mock(org.hongtu.config.service.TenantConfigDefaults.class));
        assertEquals("商户甲", controller.getConfigValue("app_name").getData());
    }
    static class ConfigService extends AppConfigServiceImpl {
        final Map<Long, String> database = new ConcurrentHashMap<>();
        ConfigService() { super(mock(BannerService.class)); }
        @Override public AppConfig getByConfigKey(String key) {
            AppConfig config = new AppConfig();
            config.setConfigKey(key);
            config.setConfigValue(database.get(TenantContext.getTenantId()));
            return config;
        }
        @Override public List<AppConfig> list() { return List.of(getByConfigKey("app_name")); }
    }

    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void sameConfigKeyIsIsolatedBetweenTenants() {
        ConfigService service = new ConfigService();
        service.database.put(1001L, "商户甲");
        service.database.put(1002L, "商户乙");
        TenantContext.setTenantId(1001L);
        assertEquals("商户甲", service.getConfigValue("app_name"));
        TenantContext.setTenantId(1002L);
        assertEquals("商户乙", service.getConfigValue("app_name"));
        TenantContext.setTenantId(1001L);
        assertEquals("商户甲", service.getConfigValue("app_name"));
    }

    @Test void refreshReplacesOnlyCurrentTenantCache() {
        ConfigService service = new ConfigService();
        service.database.put(1001L, "A");
        service.database.put(1002L, "B");
        TenantContext.setTenantId(1001L);
        service.getConfigValue("app_name");
        TenantContext.setTenantId(1002L);
        service.getConfigValue("app_name");
        service.database.put(1001L, "A2");
        service.database.put(1002L, "B2");
        TenantContext.setTenantId(1001L);
        service.refreshCache();
        assertEquals("A2", service.getConfigValue("app_name"));
        TenantContext.setTenantId(1002L);
        assertEquals("B", service.getConfigValue("app_name"));
    }

    @Test void noTenantCannotReadOrRefreshCachedConfiguration() {
        ConfigService service = new ConfigService();
        assertThrows(TenantContextMissingException.class, () -> service.getConfigValue("app_name"));
        assertThrows(TenantContextMissingException.class, service::refreshCache);
    }
}
