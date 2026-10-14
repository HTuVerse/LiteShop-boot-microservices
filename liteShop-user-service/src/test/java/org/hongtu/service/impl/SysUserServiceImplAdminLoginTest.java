package org.hongtu.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.SysUserRoleMapper;
import org.hongtu.DTO.SysTenant;
import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.VO.AdminTenantSelectionVO;
import org.hongtu.VO.AdminTotpChallengeVO;
import org.hongtu.enums.StatusEnum;
import org.hongtu.service.SysTenantService;
import org.hongtu.saas.service.SaasSubscriptionService;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
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
    @Mock
    private SaasSubscriptionService subscriptions;

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
        when(subscriptions.hasEffectiveSubscription(1001L)).thenReturn(true);
        when(subscriptions.hasEffectiveSubscription(1002L)).thenReturn(true);

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
    void passwordPreAuthenticationRejectsTenantWithoutEffectiveSubscription() {
        String encodedPassword = BCrypt.hashpw("secret123");
        SysUser user = adminUser(101L, 1001L, encodedPassword);
        when(rateLimitUtil.isAllowed(anyString(), eq(20), eq(60))).thenReturn(true);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user));
        when(sysUserRoleMapper.getRoleCodesByUserId(101L)).thenReturn(List.of("ADMIN"));
        when(sysTenantService.getById(1001L)).thenReturn(activeTenant(1001L, "B", "Beta Merchant"));
        when(subscriptions.hasEffectiveSubscription(1001L)).thenReturn(false);

        Result result = service.adminLogin("operator", "secret123");

        assertEquals(Result.CODE_FAIL, result.getCode());
        assertEquals("当前账号没有可用的管理商户", result.getMsg());
    }

    @Test
    void passwordPreAuthenticationReturnsTotpChallengeForBoundAccount() {
        String encodedPassword = BCrypt.hashpw("secret123");
        SysUser user = adminUser(101L, 1001L, encodedPassword)
                .setTotpSecret("JBSWY3DPEHPK3PXP");
        when(rateLimitUtil.isAllowed(anyString(), eq(20), eq(60))).thenReturn(true);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user));

        Result result = service.adminLogin("operator", "secret123");

        assertEquals(Result.CODE_SUCCESS, result.getCode());
        AdminTotpChallengeVO challenge = assertInstanceOf(
                AdminTotpChallengeVO.class, result.getData());
        assertTrue(challenge.isTwoFactorRequired());
        assertFalse(challenge.getChallengeTicket().isBlank());
        assertEquals(300, challenge.getExpiresInSeconds());
        verify(redisCache).setCacheObject(
                startsWith("admin:login:totp-challenge:"), eq("[101]"),
                eq(300), eq(TimeUnit.SECONDS));
    }

    @Test
    void validTotpChallengeContinuesToTenantSelection() throws Exception {
        String secret = "JBSWY3DPEHPK3PXP";
        SysUser first = adminUser(101L, 1001L, "unused").setTotpSecret(secret);
        SysUser second = adminUser(102L, 1002L, "unused").setTotpSecret(secret);
        when(rateLimitUtil.isAllowed(anyString(), eq(10), eq(300))).thenReturn(true);
        when(redisCache.getCacheObject("admin:login:totp-challenge:ticket"))
                .thenReturn("[101,102]");
        when(sysUserMapper.selectById(101L)).thenReturn(first);
        when(sysUserMapper.selectById(102L)).thenReturn(second);
        when(sysUserRoleMapper.getRoleCodesByUserId(101L)).thenReturn(List.of("ADMIN"));
        when(sysUserRoleMapper.getRoleCodesByUserId(102L)).thenReturn(List.of("STORE_ADMIN"));
        when(sysTenantService.getById(1001L)).thenReturn(activeTenant(1001L, "B", "Beta Merchant"));
        when(sysTenantService.getById(1002L)).thenReturn(activeTenant(1002L, "A", "Alpha Merchant"));
        when(subscriptions.hasEffectiveSubscription(1001L)).thenReturn(true);
        when(subscriptions.hasEffectiveSubscription(1002L)).thenReturn(true);

        Result result = service.verifyAdminTotp("ticket", currentCode(secret));

        assertEquals(Result.CODE_SUCCESS, result.getCode());
        assertInstanceOf(AdminTenantSelectionVO.class, result.getData());
        verify(redisCache).deleteObject("admin:login:totp-challenge:ticket");
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

    private String currentCode(String base32Secret) throws Exception {
        byte[] key = decodeBase32(base32Secret);
        long counter = System.currentTimeMillis() / 1000 / 30;
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1000000);
    }

    private byte[] decodeBase32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[value.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < value.length(); i++) {
            buffer = (buffer << 5) | alphabet.indexOf(value.charAt(i));
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out;
    }
}
