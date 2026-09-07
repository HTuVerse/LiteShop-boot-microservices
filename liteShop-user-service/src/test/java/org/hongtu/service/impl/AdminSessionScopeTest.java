package org.hongtu.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.SysUserRoleMapper;
import org.hongtu.DTO.SysTenant;
import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.VO.SysUserVO;
import org.hongtu.service.SysTenantService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminSessionScopeTest {
    private static final String STATEMENT = "org.hongtu.DAO.SysUserMapper.selectById";

    @AfterEach
    void clearScope() {
        TenantContext.clear();
        TenantIgnore.close();
    }

    @Test
    void platformIdentityAndRolesRemainReadableInsideSelectedTenant() {
        TenantContext.setTenantId(1001L);
        TenantContext.setSuperAdmin(true);
        SysUserMapper users = mock(SysUserMapper.class);
        SysUserRoleMapper roles = mock(SysUserRoleMapper.class);
        SysTenantService tenants = mock(SysTenantService.class);
        SysUserServiceImpl service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "sysUserMapper", users);
        ReflectionTestUtils.setField(service, "sysUserRoleMapper", roles);
        ReflectionTestUtils.setField(service, "sysTenantService", tenants);
        SysTenant tenant = new SysTenant().setId(1001L).setTenantCode("TEST").setTenantName("Test");
        when(users.selectById(1L)).thenAnswer(invocation -> {
            assertTrue(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
            return new SysUser().setId(1L).setUsername("platform");
        });
        when(roles.getRoleCodesByUserId(1L)).thenAnswer(invocation -> {
            assertTrue(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
            return List.of("SUPER_ADMIN");
        });
        when(tenants.getById(1001L)).thenAnswer(invocation -> {
            assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
            return tenant;
        });
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            Result result = service.getUserBasicInfo();
            assertEquals(Result.CODE_SUCCESS, result.getCode());
            SysUserVO user = assertInstanceOf(SysUserVO.class, result.getData());
            assertNull(user.getTenantId());
            assertEquals(1001L, user.getCurrentTenant().getId());
            assertEquals(List.of("SUPER_ADMIN"), user.getRoleCode());
            assertEquals(List.of(), user.getStoreIds());
            assertEquals(0L, user.getCoupons());
        }
        assertEquals(1001L, TenantContext.getTenantId());
        assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
    }

    @Test
    void ordinaryIdentityLookupStillUsesTenantFilterAndMissingUserExpiresSession() {
        TenantContext.setTenantId(1001L);
        TenantContext.setSuperAdmin(false);
        SysUserMapper users = mock(SysUserMapper.class);
        SysUserServiceImpl service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "sysUserMapper", users);
        when(users.selectById(2L)).thenAnswer(invocation -> {
            assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
            return null;
        });
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertEquals(401, service.getUserBasicInfo().getCode());
        }
    }

    @Test
    void onlyPlatformMenuLookupBypassesTenantAndBypassNeverLeaks() {
        TenantContext.setTenantId(1001L);
        SysPermissionMapper mapper = mock(SysPermissionMapper.class);
        SysPermissionServiceImpl service = new SysPermissionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectMenuPermissionsByUserId(any())).thenAnswer(invocation -> {
            assertEquals(TenantContext.isSuperAdmin(), InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
            return List.of();
        });
        TenantContext.setSuperAdmin(true);
        assertEquals(Result.CODE_SUCCESS, service.getUserMenuRoutes(1L).getCode());
        assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
        TenantContext.setSuperAdmin(false);
        assertEquals(Result.CODE_SUCCESS, service.getUserMenuRoutes(2L).getCode());
        assertFalse(InterceptorIgnoreHelper.willIgnoreTenantLine(STATEMENT));
        verify(mapper).selectMenuPermissionsByUserId(1L);
        verify(mapper).selectMenuPermissionsByUserId(2L);
    }
}
