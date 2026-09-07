package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.service.UserLevelSchemeService;
import org.hongtu.tenant.*;
import org.hongtu.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemberLevelTemplateGrantsTest {
    UserLevelSchemeMapper mapper;
    UserLevelMapper levels;
    SysTenantMapper tenants;
    UserLevelTemplateGrantMapper grants;
    UserLevelSchemeService service;
    @BeforeAll static void metadata() {
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"template-grants-tests");
        for(var type:List.of(UserLevelScheme.class,UserLevel.class,SysTenant.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,type);
    }
    @BeforeEach void setup() {
        mapper=mock(UserLevelSchemeMapper.class);levels=mock(UserLevelMapper.class);
        tenants=mock(SysTenantMapper.class);grants=mock(UserLevelTemplateGrantMapper.class);
        service=spy(new UserLevelSchemeService());
        ReflectionTestUtils.setField(service,"schemes",mapper);ReflectionTestUtils.setField(service,"levels",levels);
        ReflectionTestUtils.setField(service,"tenants",tenants);ReflectionTestUtils.setField(service,"grants",grants);
        TenantContext.setTenantId(10L);when(mapper.lockTenant(10L)).thenReturn(10L);
    }
    @AfterEach void cleanup(){TenantContext.clear();TenantIgnore.close();}
    UserLevelScheme template(){return new UserLevelScheme().setId(21L).setName("零售体系").setStatus(1).setIsDefault(0);}
    void platform(){TenantContext.clear();TenantContext.setSuperAdmin(true);when(mapper.selectById(21L)).thenReturn(template());}

    @Test void tenantDiscoveryUsesCurrentTenantOnly(){
        when(grants.available(10L)).thenReturn(List.of(template()));
        assertEquals(21L,service.templates().getFirst().getId());
        verify(grants).available(10L);verify(mapper,never()).selectList(any(Wrapper.class));
    }
    @Test void unassignedTenantCannotPreviewOrCopyTemplate(){
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(template());
        assertThrows(BusinessException.class,()->service.schemeLevels(21L));
        assertThrows(BusinessException.class,()->service.create("副本","",21L));
        verify(levels,never()).selectList(any(Wrapper.class));verify(mapper,never()).insert(any(UserLevelScheme.class));
    }
    @Test void assignedTenantCanPreviewButCannotEditPlatformTemplate(){
        var template=template();when(mapper.selectOne(any(Wrapper.class))).thenReturn(template);
        when(grants.count(21L,10L)).thenReturn(1);
        when(levels.selectList(any(Wrapper.class))).thenReturn(List.of(new UserLevel().setId(22L).setLevelCode(0).setRequiredExp(0).setStatus(1)));
        assertEquals(22L,service.schemeLevels(21L).getFirst().getId());
        when(mapper.selectById(21L)).thenReturn(template);
        assertThrows(BusinessException.class,()->service.writable(21L));
    }
    @Test void defaultTemplateRemainsReadableWithoutExplicitGrant(){
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(template().setIsDefault(1));
        when(levels.selectList(any(Wrapper.class))).thenReturn(List.of(new UserLevel().setId(22L)));
        assertEquals(22L,service.schemeLevels(21L).getFirst().getId());
        verify(grants,never()).count(anyLong(),anyLong());
    }
    @Test void allocationRequiresPlatformScopeEvenForSuperAdmin(){
        assertThrows(BusinessException.class,()->service.assignTenants(21L,List.of(10L)));
        TenantContext.setSuperAdmin(true);
        assertThrows(BusinessException.class,()->service.assignedTenants(21L));
        verifyNoInteractions(grants);
    }
    @Test void allocationValidatesTenantsAndDoesNotSwitchTenantSchemes(){
        platform();when(tenants.selectCount(any(Wrapper.class))).thenReturn(2L);when(grants.insertAll(21L,List.of(10L,20L))).thenReturn(2);
        service.assignTenants(21L,List.of(10L,20L,10L));
        var order=inOrder(tenants,grants);
        order.verify(tenants).selectCount(any(Wrapper.class));order.verify(grants).deleteForTemplate(21L);order.verify(grants).insertAll(21L,List.of(10L,20L));
        verify(mapper,never()).recalculateUsers(anyLong(),anyLong());verify(mapper,never()).update(any(Wrapper.class));
    }
    @Test void invalidTenantListNeverRemovesExistingGrants(){
        platform();when(tenants.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertThrows(BusinessException.class,()->service.assignTenants(21L,List.of(999L)));
        assertThrows(BusinessException.class,()->service.assignTenants(21L,null));
        verify(grants,never()).deleteForTemplate(anyLong());
    }
    @Test void clearingAllocationPreservesCopiedSchemes(){
        platform();service.assignTenants(21L,List.of());
        verify(grants).deleteForTemplate(21L);verify(grants,never()).insertAll(anyLong(),anyList());
        verifyNoInteractions(levels);verify(mapper,never()).deleteById(anyLong());
    }
    @Test void selectingAllocatedTemplateCopiesAndActivatesIt(){
        when(grants.available(10L)).thenReturn(List.of(template()));
        doReturn(new UserLevelScheme().setId(100L).setTenantId(10L)).when(service).create(anyString(),anyString(),eq(21L));
        doNothing().when(service).activate(100L);
        var selected=service.useTemplate(21L);
        assertEquals(100L,selected.getId());assertEquals(1,selected.getIsActive());verify(service).activate(100L);
    }
    @Test void selectingUnassignedTemplateNeverCreatesAScheme(){
        when(grants.available(10L)).thenReturn(List.of());
        assertThrows(BusinessException.class,()->service.useTemplate(21L));
        verify(service,never()).create(anyString(),anyString(),anyLong());
    }
    @Test void businessErrorsKeepTheirCodeAndMessage(){
        assertNull(org.springframework.core.annotation.AnnotationUtils.findAnnotation(GlobalException.class,org.springframework.web.bind.annotation.RestControllerAdvice.class));
        var result=new GlobalExceptionHandler().handleBusinessException(new BusinessException(403,"平台尚未向当前租户分配该等级体系"));
        assertEquals(403,result.getCode());assertEquals("平台尚未向当前租户分配该等级体系",result.getMsg());
    }
}
