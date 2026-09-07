package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.service.UserLevelSchemeService;
import org.hongtu.tenant.*;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemberLevelDefaultsTest {
    UserLevelSchemeMapper mapper;
    UserLevelMapper levels;
    UserLevelSchemeService service;
    @BeforeAll static void metadata() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "level-default-tests");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, UserLevelScheme.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, UserLevel.class);
    }
    @BeforeEach void setup() {
        mapper=mock(UserLevelSchemeMapper.class); levels=mock(UserLevelMapper.class);
        service=spy(new UserLevelSchemeService());
        ReflectionTestUtils.setField(service,"schemes",mapper);
        ReflectionTestUtils.setField(service,"levels",levels);
        TenantContext.setTenantId(10L);
        when(mapper.lockTenant(10L)).thenReturn(10L);
    }
    @AfterEach void cleanup() {TenantContext.clear();TenantIgnore.close();}

    @Test void initializationPreservesExistingActiveScheme() {
        var active=new UserLevelScheme().setId(11L).setTenantId(10L).setStatus(1).setIsActive(1);
        doReturn(List.of(active)).when(service).owned();
        assertSame(active,service.initializeTenant());
        verify(service,never()).create(anyString(),anyString(),any());
        verify(service,never()).activate(anyLong());
    }
    @Test void initializationChoosesMarkedDefaultRatherThanFirstTemplate() {
        doReturn(List.of()).when(service).owned();
        doReturn(List.of(new UserLevelScheme().setId(20L).setIsDefault(0),new UserLevelScheme().setId(21L).setIsDefault(1))).when(service).templates();
        var copy=new UserLevelScheme().setId(100L).setTenantId(10L);
        doReturn(copy).when(service).create(anyString(),anyString(),eq(21L));
        doNothing().when(service).activate(100L);
        assertEquals(100L,service.initializeTenant().getId());
        assertEquals(1,copy.getIsActive());
        verify(service).create(anyString(),anyString(),eq(21L));
        verify(service).activate(100L);
        assertEquals(10L,TenantContext.getTenantId());
    }
    @Test void missingDefaultRejectsInitializationInsteadOfCreatingAnIncompleteScheme() {
        doReturn(List.of()).when(service).owned();
        doReturn(List.of()).when(service).templates();
        assertThrows(BusinessException.class,()->service.initializeTenant());
        verify(mapper,never()).insert(any(UserLevelScheme.class));
    }
    @Test void copiesLevelsToTenantWithoutMutatingPlatformTemplate() {
        var template=new UserLevelScheme().setId(21L).setTenantId(null).setStatus(1).setIsDefault(1);
        var original=new UserLevel().setId(22L).setTenantId(null).setSchemeId(21L)
            .setLevelCode(0).setLevelName("普通会员").setRequiredExp(0).setStatus(1);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(template);
        when(levels.selectList(any(Wrapper.class))).thenReturn(List.of(original));
        when(mapper.insert(any(UserLevelScheme.class))).thenAnswer(call->{call.getArgument(0,UserLevelScheme.class).setId(100L);return 1;});
        when(levels.insert(any(UserLevel.class))).thenReturn(1);
        var result=service.create("商户等级","独立副本",21L);
        var captor=ArgumentCaptor.forClass(UserLevel.class);
        verify(levels).insert(captor.capture());
        assertEquals(10L,result.getTenantId());
        assertEquals(21L,result.getSourceTemplateId());
        assertEquals(100L,captor.getValue().getSchemeId());
        assertEquals(10L,captor.getValue().getTenantId());
        assertNull(captor.getValue().getId());
        assertNull(original.getTenantId());
        assertEquals(22L,original.getId());
        assertEquals(21L,original.getSchemeId());
    }
    @Test void tenantCannotChangePlatformDefault() {
        TenantContext.setSuperAdmin(true);
        assertThrows(BusinessException.class,()->service.setDefaultTemplate(21L));
        verify(mapper,never()).update(any(Wrapper.class));
    }
    @Test void disabledTemplateCannotBecomeDefault() {
        TenantContext.clear();TenantContext.setSuperAdmin(true);
        when(mapper.selectById(21L)).thenReturn(new UserLevelScheme().setId(21L).setStatus(0));
        assertThrows(BusinessException.class,()->service.setDefaultTemplate(21L));
        verify(mapper).lockTemplates();
        verify(mapper,never()).update(any(Wrapper.class));
    }
}
