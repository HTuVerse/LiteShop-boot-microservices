package org.hongtu.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.SysUserRoleMapper;
import org.hongtu.DTO.SysTenant;
import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.VO.AdminTenantSelectionVO;
import org.hongtu.enums.StatusEnum;
import org.hongtu.service.SysTenantService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.utils.RateLimitUtil;
import org.hongtu.utils.RedisCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplAdminLoginTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysTenantService sysTenantService;
    @Mock
    private RedisCache redisCache;
    @Mock
    private RateLimitUtil rateLimitUtil;

    @InjectMocks
    private SysUserServiceImpl service;
    @Mock
    private org.hongtu.service.AdminLoginSecurity adminLoginSecurity;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void customerPasswordLoginDoesNotAcceptManualTenantCodeAndRequiresPublicTenantContext() {
        Result result = service.login("customer", "secret123");

        assertEquals(Result.CODE_FAIL, result.getCode());
        assertEquals("无法识别当前商户，请从正确的商户入口进入", result.getMsg());
    }

    @Test
    void passwordPreAuthenticationReturnsAllManagedTenantsWithoutFormalToken() {
        String encodedPassword = BCrypt.hashpw("secret123");
        SysUser first = adminUser(101L, 1001L, encodedPassword);
        SysUser second = adminUser(102L, 1002L, encodedPassword);
        when(rateLimitUtil.isAllowed(anyString(), eq(20), eq(60))).thenReturn(true);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(first, second));
        when(sysUserMapper.selectById(101L)).thenReturn(first);
        when(sysUserMapper.selectById(102L)).thenReturn(second);
        when(sysUserRoleMapper.getRoleCodesByUserId(101L)).thenReturn(List.of("ADMIN"));
        when(sysUserRoleMapper.getRoleCodesByUserId(102L)).thenReturn(List.of("STORE_ADMIN"));
        when(sysTenantService.getById(1001L)).thenReturn(activeTenant(1001L, "B", "Beta Merchant"));
        when(sysTenantService.getById(1002L)).thenReturn(activeTenant(1002L, "A", "Alpha Merchant"));

        Result result = service.adminLogin("operator", "secret123");

        assertEquals(Result.CODE_SUCCESS, result.getCode());
        AdminTenantSelectionVO selection = assertInstanceOf(
                AdminTenantSelectionVO.class, result.getData());
        assertTrue(selection.isTenantSelectionRequired());
        assertFalse(selection.getSelectionTicket().isBlank());
        assertEquals(300, selection.getExpiresInSeconds());
        assertEquals(List.of("Alpha Merchant", "Beta Merchant"), selection.getTenants().stream()
                .map(option -> option.getTenantName()).toList());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redisCache).setCacheObject(
                startsWith("admin:login:tenant-selection:"), payload.capture(),
                eq(300), eq(TimeUnit.SECONDS));
        assertTrue(payload.getValue().contains("\"1001\":101"));
        assertTrue(payload.getValue().contains("\"1002\":102"));
    }

    @Test
    void selectionTicketIsConsumedBeforeRejectingAnUnauthorizedTenant() {
        when(redisCache.getAndDeleteCacheObject("admin:login:tenant-selection:ticket"))
                .thenReturn("{\"1001\":101}");

        Result result = service.selectAdminTenant("ticket", 9999L);

        assertEquals(Result.CODE_FAIL, result.getCode());
        assertEquals("无权进入所选商户，请重新登录", result.getMsg());
        verify(redisCache).getAndDeleteCacheObject("admin:login:tenant-selection:ticket");
    }

    private SysUser adminUser(Long userId, Long tenantId, String password) {
        return new SysUser()
                .setId(userId)
                .setTenantId(tenantId)
                .setUsername("operator")
                .setPassword(password)
                .setStatus(StatusEnum.NORMAL);
    }

    private SysTenant activeTenant(Long tenantId, String tenantCode, String tenantName) {
        return new SysTenant()
                .setId(tenantId)
                .setTenantCode(tenantCode)
                .setTenantName(tenantName)
                .setStatus(1);
    }
}
