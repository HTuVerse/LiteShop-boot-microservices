package org.hongtu;

import org.hongtu.api.dto.AiManagerDataRequest;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.controller.InnerAiManagerController;
import org.hongtu.restaurant.service.RestaurantAnalysisService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.StoreInfoService;
import org.hongtu.restaurant.service.TenantAnalysisService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.exception.TenantContextMissingException;
import org.hongtu.restaurant.VO.TenantAnalysisQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** AI 店长内部数据范围与最小查询模式测试。 */
class InnerAiManagerControllerTest {

    private StoreInfoService storeInfoService;
    private StoreDataScopeService scopeService;
    private RestaurantAnalysisService analysisService;
    private TenantAnalysisService tenantAnalysisService;
    private InnerAiManagerController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        storeInfoService = mock(StoreInfoService.class);
        scopeService = mock(StoreDataScopeService.class);
        analysisService = mock(RestaurantAnalysisService.class);
        tenantAnalysisService = mock(TenantAnalysisService.class);
        controller = new InnerAiManagerController(storeInfoService, scopeService,
                analysisService, tenantAnalysisService);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void platformGlobalModeCannotReadAnyTenant() {
        TenantContext.clear();
        assertThrows(TenantContextMissingException.class, controller::scope);
        assertThrows(TenantContextMissingException.class, () -> controller.context(
                new AiManagerDataRequest("TENANT", null, "TODAY", "FULL")));
        verifyNoInteractions(storeInfoService, tenantAnalysisService, analysisService);
    }

    @Test
    void tenantDishWindowIsThirtyDays() {
        controller.context(new AiManagerDataRequest("TENANT", null, "TODAY", "DISH"));
        ArgumentCaptor<TenantAnalysisQuery> captor = ArgumentCaptor.forClass(TenantAnalysisQuery.class);
        verify(tenantAnalysisService).dishRanking(captor.capture());
        assertEquals("CUSTOM", captor.getValue().getTimeRange());
        assertEquals(LocalDate.now().minusDays(29), captor.getValue().getStartDate());
        assertEquals(LocalDate.now(), captor.getValue().getEndDate());
        verify(tenantAnalysisService, never()).overview(any());
    }

    @Test
    void restrictedStoreAccountCannotReadTenantContext() {
        when(scopeService.requiresStoreRestriction()).thenReturn(true);

        assertThrows(SecurityException.class, () -> controller.context(
                new AiManagerDataRequest("TENANT", null, "TODAY", "BRIEFING")));

        verifyNoInteractions(tenantAnalysisService);
    }

    @Test
    void storeOutsideCurrentTenantIsRejected() {
        when(storeInfoService.getById(999L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> controller.context(
                new AiManagerDataRequest("STORE", 999L, "TODAY", "FULL")));

        verifyNoInteractions(analysisService);
    }

    @Test
    void dishModeDoesNotLoadBriefingMetrics() {
        when(storeInfoService.getById(12L)).thenReturn(new StoreInfo().setId(12L).setStoreName("中心店"));

        controller.context(new AiManagerDataRequest("STORE", 12L, "TODAY", "DISH"));

        verify(analysisService, never()).getSalesOverview(anyLong(), isNull(), any(), any());
        verify(analysisService, never()).getOrderAnalysis(anyLong(), isNull(), any(), any());
    }
}
