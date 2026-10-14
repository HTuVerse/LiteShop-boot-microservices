package org.hongtu;

import org.hongtu.restaurant.controller.TenantAnalysisController;
import org.hongtu.restaurant.service.TenantAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 租户分析主路径可命中已有网关餐饮分析路由，并保留旧路径兼容。 */
class TenantAnalysisControllerTest {
    @Test
    void allEndpointsSupportRestaurantRouteAndLegacyAlias() throws Exception {
        var service = mock(TenantAnalysisService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new TenantAnalysisController(service)).build();
        for (String prefix : new String[]{"/restaurant-analysis/tenant", "/tenant-analysis"}) {
            for (String endpoint : new String[]{"overview", "store-ranking", "dish-ranking", "store-dish-matrix"}) {
                mvc.perform(post(prefix + "/" + endpoint).contentType("application/json").content("{}"))
                        .andExpect(status().isOk());
            }
            mvc.perform(post(prefix + "/store-dish-comparison").contentType("application/json").content("{}"))
                    .andExpect(status().isOk());
        }
        verify(service, times(2)).overview(any());
        verify(service, times(2)).storeRanking(any());
        verify(service, times(2)).dishRanking(any());
        verify(service, times(2)).storeDishMatrix(any());
        verify(service, times(2)).storeDishComparison(any());
    }
}
