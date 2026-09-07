package org.hongtu.config.service.impl;
import org.hongtu.config.service.TenantConfigDefaults;
import org.hongtu.config.DAO.AppConfigMapper;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TenantDefaultsTest {
    @AfterEach void cleanup(){TenantContext.clear();}
    @Test void defaultsUseCurrentTenantAndUnknownKeysAreNotSilentlyCreated(){
        var mapper=mock(AppConfigMapper.class);var defaults=new TenantConfigDefaults();ReflectionTestUtils.setField(defaults,"mapper",mapper);
        TenantContext.setTenantId(10L);defaults.ensureDefaults("青禾餐饮");
        verify(mapper).insertDefault(anyLong(),eq(10L),eq("app_name"),eq("青禾餐饮"),eq(2),eq(2),anyString());
        TenantContext.setTenantId(20L);defaults.ensureKey("theme_color");
        verify(mapper).insertDefault(anyLong(),eq(20L),eq("theme_color"),eq("#FF6B35"),eq(3),eq(4),anyString());
        clearInvocations(mapper);defaults.ensureKey("unknown_secret");verifyNoInteractions(mapper);
        assertEquals(TenantConfigDefaults.ENTRIES.size(),TenantConfigDefaults.ENTRIES.stream().map(TenantConfigDefaults.Entry::key).distinct().count());
    }
}
