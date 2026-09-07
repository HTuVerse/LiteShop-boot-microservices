package org.hongtu.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.Result.Result;
import org.hongtu.enums.StatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoleAssignmentScopeTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final SysUserRoleMapper links = mock(SysUserRoleMapper.class);
    private final SysUserRoleServiceImpl service = new SysUserRoleServiceImpl();
    RoleAssignmentScopeTest() {
        ReflectionTestUtils.setField(service,"sysUserMapper",users);
        ReflectionTestUtils.setField(service,"sysRoleMapper",roles);
        ReflectionTestUtils.setField(service,"baseMapper",links);
        when(users.selectOne(any())).thenReturn(new SysUser().setId(20L).setTenantId(10L));
        when(links.selectRoleIdsByUserId(20L)).thenReturn(List.of());
        when(roles.selectRoleCodesByUserId(20L)).thenReturn(List.of("USER"));
    }
    @Test void customUserManagerCannotClearAdministratorRoles() {
        when(roles.selectRoleCodesByUserId(20L)).thenReturn(List.of("ADMIN"));
        try(var stp=mockStatic(StpUtil.class)) {
            assertNotEquals(Result.CODE_SUCCESS,service.assignRoles(20L,List.of()).getCode());
        }
        verify(links,never()).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
    @Test void roleAssignmentCannotBypassPermissionDelegationLimits() {
        when(roles.selectByIds(List.of(4L))).thenReturn(List.of(new SysRole().setId(4L).setTenantId(10L).setRoleCode("FINANCE").setStatus(StatusEnum.NORMAL)));
        when(roles.selectPermissionsByRoleId(4L)).thenReturn(List.of(new SysPermission().setId(8L).setPermissionCode("restaurant:analysis")));
        try(var stp=mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getPermissionList).thenReturn(List.of("system:user"));
            assertNotEquals(Result.CODE_SUCCESS,service.assignRoles(20L,List.of(4L)).getCode());
        }
        verify(links,never()).insert(any(SysUserRole.class));
    }
    @Test void crossTenantRoleRejectedBeforeWriting() {
        when(roles.selectByIds(List.of(4L))).thenReturn(List.of(new SysRole().setId(4L).setTenantId(99L).setRoleCode("STAFF").setStatus(StatusEnum.NORMAL)));
        try(var stp=mockStatic(StpUtil.class)) {
            stp.when(()->StpUtil.hasRole("ADMIN")).thenReturn(true);
            assertNotEquals(Result.CODE_SUCCESS,service.assignRoles(20L,List.of(4L)).getCode());
        }
        verify(links,never()).insert(any(SysUserRole.class));
    }
    @Test void unchangedAllowedRoleIsIdempotent() {
        when(links.selectRoleIdsByUserId(20L)).thenReturn(List.of(4L));
        when(roles.selectByIds(List.of(4L))).thenReturn(List.of(new SysRole().setId(4L).setTenantId(10L).setRoleCode("STAFF").setStatus(StatusEnum.NORMAL)));
        try(var stp=mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getPermissionList).thenReturn(List.of("system:user"));
            assertEquals(Result.CODE_SUCCESS,service.assignRoles(20L,List.of(4L,4L)).getCode());
        }
        verify(links,never()).insert(any(SysUserRole.class));
        verify(links,never()).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
