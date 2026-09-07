package org.hongtu.service.impl;
import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.service.UserLevelSchemeService;
import org.hongtu.tenant.*;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class MemberLevelSchemeTest {
    @org.junit.jupiter.api.BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
            new com.baomidou.mybatisplus.core.MybatisConfiguration(), "regression-tests"), org.hongtu.DTO.UserLevelScheme.class);
    }
    @AfterEach void cleanup(){TenantContext.clear();TenantIgnore.close();}
    @Test void rejectsCrossTenantSchemeAndPlatformTemplateWrites(){
        var mapper=mock(UserLevelSchemeMapper.class);var service=new UserLevelSchemeService();ReflectionTestUtils.setField(service,"schemes",mapper);
        TenantContext.setTenantId(10L);
        when(mapper.selectById(8L)).thenReturn(new UserLevelScheme().setId(8L).setTenantId(20L));
        assertThrows(BusinessException.class,()->service.writable(8L));
        when(mapper.selectById(8L)).thenReturn(new UserLevelScheme().setId(8L).setTenantId(null));
        assertThrows(BusinessException.class,()->service.writable(8L));
        verify(mapper,never()).deleteById(anyLong());
    }
    @Test void validatesBaselineAndDistinctEnabledThresholds(){
        var service=new UserLevelSchemeService();
        var base=new UserLevel().setLevelCode(0).setRequiredExp(0).setStatus(1);
        var higher=new UserLevel().setLevelCode(1).setRequiredExp(100).setStatus(1);
        assertDoesNotThrow(()->service.validateLevels(List.of(base,higher)));
        assertThrows(BusinessException.class,()->service.validateLevels(List.of(higher)));
        higher.setRequiredExp(0);assertThrows(BusinessException.class,()->service.validateLevels(List.of(base,higher)));
        higher.setStatus(0);assertDoesNotThrow(()->service.validateLevels(List.of(base,higher)));
    }
    @Test void switchingSchemeRecalculatesOnlyCurrentTenantAfterValidation(){
        var mapper=mock(UserLevelSchemeMapper.class);var levels=mock(UserLevelMapper.class);var service=new UserLevelSchemeService();
        ReflectionTestUtils.setField(service,"schemes",mapper);ReflectionTestUtils.setField(service,"levels",levels);TenantContext.setTenantId(10L);
        when(mapper.lockTenant(10L)).thenReturn(10L);
        when(mapper.selectById(8L)).thenReturn(new UserLevelScheme().setId(8L).setTenantId(10L).setStatus(1));
        when(levels.selectList(any())).thenReturn(List.of(new UserLevel().setId(1L).setLevelCode(0).setRequiredExp(0).setStatus(1)));
        when(mapper.update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        service.activate(8L);verify(mapper).recalculateUsers(10L,8L);verify(mapper,never()).recalculateUsers(eq(20L),anyLong());
    }
    @Test void businessLookupsCarryExplicitTenantEvenForPlatformContext(){
        var mapper=mock(UserLevelMapper.class,CALLS_REAL_METHODS);
        TenantContext.setTenantId(10L);mapper.getLevelByExp(100);verify(mapper).getLevelByExpScoped(100,10L);
        TenantContext.setTenantId(20L);mapper.getLevelByExp(100);verify(mapper).getLevelByExpScoped(100,20L);
        TenantContext.clear();mapper.getLevelByLevelCode(1);verify(mapper).getLevelByLevelCodeScoped(1,null);
    }
}
