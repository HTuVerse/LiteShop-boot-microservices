package org.hongtu.ai.application;

import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.Result.Result;
import org.hongtu.ai.client.AiAvailabilityService;
import org.hongtu.ai.client.AiBusinessDataClient;
import org.hongtu.ai.client.AiRuntimeModelService;
import org.hongtu.ai.config.AiToolCallingConfig;
import org.hongtu.ai.model.AiManagerRequest;
import org.hongtu.ai.model.AiManagerResponse;
import org.hongtu.ai.prompt.AiPromptRenderer;
import org.hongtu.ai.tool.AiManagerReadTools;
import org.hongtu.api.client.RestaurantFeignClient;
import org.hongtu.api.dto.AiManagerDataRequest;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.dto.AiAdvisorSessionDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 验证模型故障不再返回伪分析，真实模型结果保持经营范围鉴权。 */
class AiManagerServiceTest {
    private static final String BRIEFING_JSON = """
            {"keyPoints":[{"title":"营业表现稳定","detail":"销售额来自给定数据"}],"evidence":[{"level":"INFO","title":"销售额","detail":"来自给定数据"}],"todayTasks":[{"title":"核对退菜","description":"查看今日退菜","risk":"样本不足"}]}
            """;

    private RestaurantFeignClient restaurant;
    private AiRuntimeModelService models;
    private AiManagerQuotaService quota;
    private AiManagerService service;
    private AiWorkspaceService workspace;
    private MockedStatic<StpUtil> login;

    @BeforeEach
    void setUp() {
        restaurant = mock(RestaurantFeignClient.class);
        models = mock(AiRuntimeModelService.class);
        quota = mock(AiManagerQuotaService.class);
        workspace = mock(AiWorkspaceService.class);
        when(workspace.openSession(any())).thenAnswer(call -> {
            AiManagerRequest request = call.getArgument(0);
            return new AiAdvisorSessionDTO().setId(1L)
                    .setScopeType(request.getScopeType()).setStoreId(request.getStoreId());
        });
        when(quota.diagnosisRecords(anyString(), any(), anyString())).thenReturn(List.of());
        when(quota.reserve(anyString(), any())).thenReturn(new AiGenerationDTO().setAcquired(true));
        when(restaurant.getAiManagerScope()).thenReturn(Result.success(Map.of(
                "tenantViewAvailable", true,
                "stores", List.of(Map.of("storeId", 12L, "storeName", "测试店"))
        )));
        ObjectMapper json = JsonMapper.builder().build();
        AiBusinessDataClient data = new AiBusinessDataClient(restaurant);
        AiManagerScopeGuard guard = new AiManagerScopeGuard(data);
        service = new AiManagerService(data, new AiAvailabilityService(models), quota, guard,
                new AiPromptRenderer(), new AiManagerReadTools(data, guard, json), json,
                new AiToolCallingConfig().aiManagerToolCallingAdvisor(), workspace);
        // 单测中同步执行后台任务，便于直接断言诊断生成结果。
        ReflectionTestUtils.setField(service, "taskExecutor", (TaskExecutor) Runnable::run);
        login = mockStatic(StpUtil.class);
        login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    }

    @AfterEach
    void tearDown() {
        login.close();
        TenantContext.clear();
    }

    @Test
    void allCapabilitiesRejectMissingModelBeforeReadingBusinessData() {
        AiManagerRequest request = new AiManagerRequest();
        request.setMessage("提高晚餐订单");
        for (Runnable call : List.<Runnable>of(() -> service.briefing(request),
                () -> service.dishAdvice(request), () -> service.marketingDraft(request), () -> service.chat(request))) {
            BusinessException error = assertThrows(BusinessException.class, call::run);
            assertEquals(503, error.getCode());
            assertTrue(error.getMessage().contains("暂不可用"));
        }
        verifyNoInteractions(restaurant);
    }

    @Test
    void successfulAnalysisUsesModelAndPassesAuthorizedScope() {
        ChatModel model = usableModel();
        when(model.call(any(Prompt.class))).thenReturn(textResponse(BRIEFING_JSON));
        when(restaurant.getAiManagerContext(any())).thenReturn(Result.success(Map.of(
                "scopeType", "STORE", "storeId", 12L)));
        AiManagerRequest request = new AiManagerRequest();
        request.setCreatedBy(999L);
        request.setRequestId(UUID.randomUUID().toString());
        request.setScopeType("STORE");
        request.setStoreId(12L);
        request.setHistory(List.of(
                new AiManagerRequest.ConversationMessage("user", "昨天呢"),
                new AiManagerRequest.ConversationMessage("assistant", "昨天销售额下降")));
        var response = service.briefing(request);
        assertEquals(7L, request.getCreatedBy());
        assertTrue(response.modelGenerated());
        assertEquals("", response.content());
        assertEquals(1, response.keyPoints().size());
        assertEquals("营业表现稳定", response.keyPoints().getFirst().title());
        assertEquals(1, response.evidence().size());
        assertEquals("INFO", response.evidence().getFirst().level());
        assertEquals(1, response.actions().size());
        assertTrue(response.actions().getFirst().requiresConfirmation());
        assertEquals(12L, response.scope().get("storeId"));
        var captor = ArgumentCaptor.forClass(AiManagerDataRequest.class);
        verify(restaurant).getAiManagerContext(captor.capture());
        assertEquals("DIAGNOSIS", captor.getValue().dataMode());
        assertEquals(12L, captor.getValue().storeId());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertEquals(4096, prompt.getValue().getOptions().getMaxTokens());
        assertFalse(toolCallbacks(prompt.getValue()));
        assertEquals(4, prompt.getValue().getInstructions().size());
        assertTrue(prompt.getValue().getInstructions().get(1).getText().contains("昨天呢"));
        assertFalse(prompt.getValue().getInstructions().getLast().getText().contains("昨天呢"));
        verify(quota).complete(eq(request.getRequestId()), any(), any(ChatResponse.class));
    }

    @Test
    void malformedStructuredOutputIsRetriedOnceWithoutReservingQuotaAgain() {
        ChatModel model = usableModel();
        when(model.call(any(Prompt.class)))
                .thenReturn(textResponse("not-json"))
                .thenReturn(textResponse(BRIEFING_JSON));
        when(restaurant.getAiManagerContext(any())).thenReturn(Result.success(Map.of("scopeType", "TENANT")));
        AiManagerRequest request = new AiManagerRequest();
        request.setRequestId(UUID.randomUUID().toString());

        var response = service.briefing(request);

        assertEquals("营业表现稳定", response.keyPoints().getFirst().title());
        verify(model, times(2)).call(any(Prompt.class));
        verify(quota, times(1)).reserve("BRIEFING", request);
    }

    @Test
    void marketingGoalStaysOutOfSystemPrompt() {
        ChatModel model = usableModel();
        when(model.call(any(Prompt.class))).thenReturn(textResponse("""
                {"summary":"草稿结论","audience":"晚餐顾客","mechanism":"满减","budgetConstraint":"100元","risk":"毛利下降","reviewMetrics":"核销率"}
                """));
        when(restaurant.getAiManagerContext(any())).thenReturn(Result.success(Map.of("scopeType", "TENANT")));
        AiManagerRequest request = new AiManagerRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setMessage("秘密目标只应出现在用户消息");
        var response = service.marketingDraft(request);
        assertTrue(response.content().contains("尚未创建活动"));
        assertTrue(response.actions().getFirst().requiresConfirmation());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        String system = prompt.getValue().getInstructions().stream()
                .filter(SystemMessage.class::isInstance).map(Message::getText).findFirst().orElse("");
        String user = prompt.getValue().getInstructions().getLast().getText();
        assertFalse(system.contains("秘密目标"));
        assertTrue(user.contains("秘密目标"));
        assertTrue(user.contains("未设置"));
    }

    @Test
    void chatUsesReadOnlyToolsWithoutPrefetchingMetrics() {
        ChatModel model = usableModel();
        // 运行时模型选项同时支持工具和结构化输出，合并后回调才会留在发给模型的 Prompt 上。
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(model.call(any(Prompt.class))).thenReturn(textResponse("""
                {"answer":"晚餐下滑","evidence":[{"level":"WARNING","title":"订单","detail":"来自工具"}],"missingData":"缺少成本"}
                """));
        AiManagerRequest request = new AiManagerRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setMessage("晚餐为什么下滑");
        var response = service.chat(request);
        assertTrue(response.result().content().contains("晚餐下滑"));
        assertTrue(response.result().content().contains("缺少成本"));
        assertEquals("WARNING", response.result().evidence().getFirst().level());
        verify(restaurant, never()).getAiManagerContext(any());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertTrue(toolCallbacks(prompt.getValue()), String.valueOf(prompt.getValue().getOptions()));
        assertFalse(prompt.getValue().getInstructions().stream()
                .filter(SystemMessage.class::isInstance).map(Message::getText).findFirst().orElse("")
                .contains("晚餐为什么下滑"));
    }

    @Test
    void dashboardRejectsNumbersThatNoToolActuallyReturned() {
        ChatModel model = usableModel();
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(model.call(any(Prompt.class))).thenReturn(textResponse("""
                {"headline":"晚餐承压","report":"晚餐销售额下滑，需关注。","cards":[
                  {"id":"c1","type":"RISK","level":"WARNING","order":1,"size":"MD","title":"销售额下降",
                   "body":"近7天环比下降","metricCode":"sales_drop_rate","value":"12","unit":"%"},
                  {"id":"c2","type":"CHART","level":"INFO","order":2,"size":"LG","title":"销售趋势",
                   "chart":{"chartType":"line","categories":["周一","周二"],"series":[{"name":"销售额","data":[100,80]}],"unit":"元"}},
                  {"id":"c3","type":"CHART","level":"INFO","order":3,"size":"MD","title":"无数据图","chart":{"chartType":"bar","series":[]}}
                ]}
                """));
        // 诊断仪表盘改为后台异步生成，预留记录后立即返回请求ID；后台任务需要租户上下文。
        TenantContext.setTenantId(42L);
        when(quota.reserve(eq("DIAGNOSIS_DASHBOARD"), any(), eq(false)))
                .thenReturn(new AiGenerationDTO().setAcquired(true));
        AiManagerRequest request = new AiManagerRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setScopeType("STORE");
        request.setStoreId(12L);

        var submission = service.submitDashboard(request);
        assertEquals(request.getRequestId(), submission.requestId());
        assertEquals("PROCESSING", submission.status());

        // 模型未调用工具却返回数字时，后台任务记为失败，不保存伪造的诊断。
        verify(quota, never()).complete(eq(request.getRequestId()), any(), any());
        verify(quota).fail(eq(request.getRequestId()), anyString(), anyString(), anyBoolean());
        verify(restaurant, never()).getAiManagerContext(any());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertTrue(toolCallbacks(prompt.getValue()));
    }

    @Test
    void unauthorizedTenantScopeFailsBeforeReservation() {
        when(models.currentModel()).thenReturn(mock(ChatModel.class));
        when(restaurant.getAiManagerScope()).thenReturn(Result.success(Map.of(
                "tenantViewAvailable", false, "stores", List.of())));
        AiManagerRequest request = new AiManagerRequest();
        request.setRequestId(UUID.randomUUID().toString());
        BusinessException error = assertThrows(BusinessException.class, () -> service.briefing(request));
        assertEquals(403, error.getCode());
        verify(quota, never()).reserve(anyString(), any());
    }

    @Test
    void blankQuestionIsRejectedAsBadRequest() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.chat(new AiManagerRequest()));
        assertEquals(400, error.getCode());
    }

    @Test
    void retryReturnsSavedResultWithoutSecondModelCall() {
        ChatModel model = mock(ChatModel.class);
        when(models.currentModel()).thenReturn(model);
        when(quota.reserve(anyString(), any())).thenReturn(new AiGenerationDTO().setStatus("SUCCEEDED"));
        var saved = new org.hongtu.ai.model.AiManagerResponse("BRIEFING", "经营简报", "已保存",
                List.of(), Map.of("scopeType", "TENANT"), List.of(), List.of(), true,
                java.time.LocalDateTime.now(), null);
        when(quota.cached(any())).thenReturn(saved);
        AiManagerRequest request = new AiManagerRequest();
        request.setRequestId(UUID.randomUUID().toString());

        assertEquals("已保存", service.briefing(request).content());
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void quotaFailureAndEmptyResponseNeverBecomeSuccessfulRuleAnalysis() {
        login.when(() -> StpUtil.hasRole("SUPER_ADMIN")).thenReturn(true);
        ChatModel model = usableModel();
        when(restaurant.getAiManagerContext(any())).thenReturn(Result.success(Map.of("scopeType", "TENANT")));
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("429 insufficient_quota secret-key"));
        var quotaError = assertThrows(BusinessException.class, () -> service.briefing(new AiManagerRequest()));
        assertTrue(quotaError.getMessage().contains("额度"));
        assertFalse(quotaError.getMessage().contains("secret-key"));
        reset(model);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(textResponse(""));
        assertThrows(BusinessException.class, () -> service.dishAdvice(new AiManagerRequest()));
    }

    @Test
    void businessPermissionFailureIsNotReportedAsModelFailure() {
        when(models.currentModel()).thenReturn(mock(ChatModel.class));
        when(restaurant.getAiManagerContext(any())).thenThrow(new SecurityException("门店无权访问"));
        assertThrows(SecurityException.class, () -> service.briefing(new AiManagerRequest()));
    }

    private ChatModel usableModel() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(models.currentModel()).thenReturn(model);
        return model;
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static boolean toolCallbacks(Prompt prompt) {
        return prompt.getOptions() instanceof ToolCallingChatOptions toolOptions
                && toolOptions.getToolCallbacks() != null
                && !toolOptions.getToolCallbacks().isEmpty();
    }
}
