package org.hongtu.ai.tool;

import org.hongtu.Result.Result;
import org.hongtu.ai.application.AiManagerScopeGuard;
import org.hongtu.ai.application.AiDashboardFactRegistry;
import org.hongtu.ai.client.AiBusinessDataClient;
import org.hongtu.api.client.RestaurantFeignClient;
import org.hongtu.api.dto.AiManagerDataRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证经营追问工具不能越过当前授权范围，也不能把超限数据交回模型。 */
class AiManagerReadToolsTest {
    private RestaurantFeignClient restaurant;
    private AiManagerReadTools tools;

    @BeforeEach
    void setUp() {
        restaurant = mock(RestaurantFeignClient.class);
        when(restaurant.getAiManagerScope()).thenReturn(Result.success(Map.of(
                "tenantViewAvailable", true,
                "stores", List.of(Map.of("storeId", 12L, "storeName", "测试店", "status", 1, "phone", "13800000000"))
        )));
        AiBusinessDataClient data = new AiBusinessDataClient(restaurant);
        tools = new AiManagerReadTools(data, new AiManagerScopeGuard(data), JsonMapper.builder().build());
    }

    @Test
    void storeConversationCannotSwitchStore() {
        String result = tools.getSalesOverview("TODAY", 99L, context("STORE", 12L, "TODAY"));
        assertTrue(result.contains("无权"));
        verify(restaurant, never()).getAiManagerContext(any());
    }

    @Test
    void authorizedStoreQueryUsesBriefingModeAndDropsPersonalFields() {
        when(restaurant.getAiManagerContext(any())).thenReturn(Result.success(Map.of("scopeType", "STORE", "storeId", 12L)));
        String result = tools.getSalesOverview(null, null, context("STORE", 12L, "WEEK"));
        assertTrue(result.contains("\"storeId\":12"));
        var captor = ArgumentCaptor.forClass(AiManagerDataRequest.class);
        verify(restaurant).getAiManagerContext(captor.capture());
        assertEquals("BRIEFING", captor.getValue().dataMode());
        assertEquals("WEEK", captor.getValue().timeRange());
        assertEquals(12L, captor.getValue().storeId());

        String stores = tools.listAuthorizedStores(context("STORE", 12L, "WEEK"));
        assertTrue(stores.contains("测试店"));
        assertFalse(stores.contains("13800000000"));
    }

    @Test
    void invalidTimeAndOversizedPayloadStayInsideTheTool() {
        assertTrue(tools.getSalesOverview("YEAR", null, context("TENANT", null, "TODAY")).contains("不支持的时间范围"));
        Map<String, Object> huge = new LinkedHashMap<>();
        huge.put("overview", "x".repeat(AiManagerReadTools.MAX_RESULT_CHARS));
        when(restaurant.getAiManagerContext(any())).thenReturn(Result.success(huge));
        assertTrue(tools.getDishPerformance(null, context("TENANT", null, "TODAY")).contains("超出单次工具返回上限"));
    }

    @Test
    void successfulReadExposesSourceIdForItsActualNumericResult() {
        when(restaurant.getAiManagerContext(any())).thenReturn(Result.success(Map.of(
                "scopeType", "STORE", "storeId", 12L, "overview", Map.of("sales", 158))));
        var facts = new AiDashboardFactRegistry(JsonMapper.builder().build());
        var values = new LinkedHashMap<String, Object>();
        values.put("scopeType", "STORE");
        values.put("storeId", 12L);
        values.put("timeRange", "WEEK");
        values.put("dashboardFacts", facts);

        String response = tools.getSalesOverview(null, null, new ToolContext(values));
        String sourceId = JsonMapper.builder().build().readTree(response).get("sourceId").asText();

        assertEquals(new java.math.BigDecimal("158"), facts.resolve(sourceId, "/overview/sales").value());
        assertEquals("WEEK", facts.resolve(sourceId, "/overview/sales").timeRange());
    }

    private static ToolContext context(String scopeType, Long storeId, String timeRange) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("scopeType", scopeType);
        if (storeId != null) values.put("storeId", storeId);
        values.put("timeRange", timeRange);
        return new ToolContext(values);
    }
}
