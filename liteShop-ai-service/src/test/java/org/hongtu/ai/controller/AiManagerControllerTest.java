package org.hongtu.ai.controller;

import org.hongtu.Result.Result;
import org.hongtu.ai.application.AiManagerService;
import org.hongtu.ai.application.AiWorkspaceService;
import org.hongtu.ai.client.AiAvailabilityService;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证 AI 店长接口每次都按当前租户的生效套餐权益放行。 */
class AiManagerControllerTest {
    private final AiManagerService manager = mock(AiManagerService.class);
    private final AiAvailabilityService availability = mock(AiAvailabilityService.class);
    private final UserFeignClient user = mock(UserFeignClient.class);
    private final AiWorkspaceService workspace = mock(AiWorkspaceService.class);
    private final AiManagerController controller = new AiManagerController(
            manager, availability, user, workspace);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void unavailableForTenantWithoutAiEntitlement() {
        TenantContext.setTenantId(42L);
        when(user.checkEntitlement(any())).thenReturn(Result.success(
                new EntitlementDecisionDTO().setAllowed(false).setReason("FEATURE_DISABLED")));

        AiAvailabilityService.Status status = controller.status();
        assertFalse(status.enabled());
        assertEquals("NOT_ENTITLED", status.reason());
        assertTrue(status.message().contains("加购"));
        BusinessException error = assertThrows(BusinessException.class, controller::scope);
        assertEquals(403, error.getCode());
        verifyNoInteractions(manager, availability);
    }

    @Test
    void platformGlobalViewHasNoTenantEntitlement() {
        AiAvailabilityService.Status status = controller.status();
        assertFalse(status.enabled());
        assertEquals("NOT_ENTITLED", status.reason());
        assertThrows(BusinessException.class, controller::scope);
        verifyNoInteractions(user, manager, availability);
    }

    @Test
    void diagnosisDashboardRejectedWithoutEntitlement() {
        TenantContext.setTenantId(42L);
        when(user.checkEntitlement(any())).thenReturn(Result.success(
                new EntitlementDecisionDTO().setAllowed(false).setReason("FEATURE_DISABLED")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.diagnosisDashboard(new org.hongtu.ai.model.AiManagerRequest()));
        assertEquals(403, error.getCode());
        verifyNoInteractions(manager);
    }

    @Test
    void enabledTenantCanUseAiManager() {
        TenantContext.setTenantId(42L);
        when(user.checkEntitlement(any())).thenReturn(Result.success(
                new EntitlementDecisionDTO().setAllowed(true)));
        AiAvailabilityService.Status ready = new AiAvailabilityService.Status(true, "READY", "", false);
        when(availability.status()).thenReturn(ready);
        when(manager.scope()).thenReturn(Map.of("tenantViewAvailable", true));

        assertEquals(ready, controller.status());
        assertEquals(true, controller.scope().get("tenantViewAvailable"));
        ArgumentCaptor<EntitlementMutationDTO> requests = ArgumentCaptor.forClass(EntitlementMutationDTO.class);
        verify(user, times(2)).checkEntitlement(requests.capture());
        assertTrue(requests.getAllValues().stream().allMatch(request ->
                SaasFeatureCode.AI_MANAGER.equals(request.getFeatureCode())
                        && BigDecimal.ZERO.compareTo(request.getAmount()) == 0));
    }
}
