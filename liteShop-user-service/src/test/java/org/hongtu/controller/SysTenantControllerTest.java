package org.hongtu.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.DTO.SysTenant;
import org.hongtu.Result.Result;
import org.hongtu.service.SysTenantService;
import org.hongtu.tenant.TenantAccessValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 租户工作空间切换接口测试。 */
@ExtendWith(MockitoExtension.class)
class SysTenantControllerTest {

    @Mock
    private SysTenantService tenantService;

    @Mock
    private TenantAccessValidator tenantAccessValidator;

    @InjectMocks
    private SysTenantController controller;

    @Test
    void enterTenantRejectsMissingSubscriptionBeforeChangingSession() {
        SysTenant tenant = new SysTenant().setId(1001L).setStatus(1);
        when(tenantService.getById(1001L)).thenReturn(tenant);
        when(tenantAccessValidator.isActive(1001L)).thenReturn(false);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            Result<SysTenant> result = controller.enterTenant(1001L);

            assertEquals(Result.CODE_FORBIDDEN, result.getCode());
            assertEquals("商户未分配有效套餐或套餐已失效", result.getMsg());
            stp.verifyNoInteractions();
        }
        verify(tenantAccessValidator).isActive(1001L);
    }
}
