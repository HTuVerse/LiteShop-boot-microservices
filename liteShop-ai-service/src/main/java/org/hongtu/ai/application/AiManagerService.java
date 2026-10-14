package org.hongtu.ai.application;

import org.hongtu.ai.client.AiAvailabilityService;
import org.hongtu.ai.client.AiBusinessDataClient;
import org.hongtu.ai.client.AiStructuredOutputParser;
import org.hongtu.ai.prompt.AiPromptRenderer;
import org.hongtu.ai.tool.AiManagerReadTools;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import org.hongtu.ai.model.AiDashboardResult;
import org.hongtu.ai.model.AiDashboardSubmission;
import org.hongtu.ai.model.AiInsightSubmission;
import org.hongtu.ai.model.AiAdvisorReply;
import org.hongtu.ai.model.AiManagerRecord;
import org.hongtu.ai.model.AiManagerRequest;
import org.hongtu.ai.model.AiManagerResponse;
import org.hongtu.ai.model.BriefingResult;
import org.hongtu.ai.model.ChatAnswerResult;
import org.hongtu.ai.model.DishAdviceResult;
import org.hongtu.ai.model.MarketingDraftResult;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.dto.AiManagerDataRequest;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.StoreContext;
import org.hongtu.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;

/** AI 店长编排服务。简报、菜品和营销一次读取有界指标；经营追问只通过只读工具取数。 */
@Service
public class AiManagerService {
    private static final Logger log = LoggerFactory.getLogger(AiManagerService.class);
    private static final int MAX_CONTEXT_CHARS = 24000;

    private final AiBusinessDataClient businessData;
    private final AiAvailabilityService availability;
    private final AiManagerQuotaService quota;
    private final AiManagerScopeGuard scopeGuard;
    private final AiPromptRenderer prompts;
    private final AiManagerReadTools readTools;
    private final ObjectMapper objectMapper;
    private final ToolCallingAdvisor chatToolAdvisor;
    private final AiWorkspaceService workspace;
    @Resource(name = "aiManagerTaskExecutor")
    private TaskExecutor taskExecutor;

    public AiManagerService(AiBusinessDataClient businessData, AiAvailabilityService availability,
                            AiManagerQuotaService quota, AiManagerScopeGuard scopeGuard,
                            AiPromptRenderer prompts, AiManagerReadTools readTools, ObjectMapper objectMapper,
                            ToolCallingAdvisor chatToolAdvisor, AiWorkspaceService workspace) {
        this.businessData = businessData;
        this.availability = availability;
        this.quota = quota;
        this.scopeGuard = scopeGuard;
        this.prompts = prompts;
        this.readTools = readTools;
        this.objectMapper = objectMapper;
        this.chatToolAdvisor = chatToolAdvisor;
        this.workspace = workspace;
    }

    /** 查询当前用户经过服务端鉴权后的 AI 店长可选范围。 */
    public Map<String, Object> scope() {
        return scopeGuard.scope();
    }

    /** 根据模型状态、授权门店和可用经营数据计算前端能力。 */
    public Map<String, Boolean> capabilities(boolean modelReady) {
        Map<String, Object> scopeInfo;
        try {
            scopeInfo = businessData.scope();
            if (scopeInfo == null) scopeInfo = Map.of();
        } catch (RuntimeException error) {
            log.warn("AI 店长能力范围探测失败，异常类型：{}", error.getClass().getSimpleName());
            scopeInfo = Map.of();
        }
        List<?> stores = list(scopeInfo.get("stores"));
        String scopeType = String.valueOf(scopeInfo.getOrDefault("defaultScopeType", "TENANT"));
        Long storeId = "STORE".equalsIgnoreCase(scopeType)
                ? longValue(scopeInfo.get("defaultStoreId")) : null;
        Map<String, Object> analysis = Map.of();
        try {
            analysis = businessData.context(new AiManagerDataRequest(
                    scopeType.toUpperCase(Locale.ROOT), storeId, "TODAY", "ANALYSIS"));
            if (analysis == null) analysis = Map.of();
        } catch (RuntimeException error) {
            log.warn("AI 店长数据能力探测失败，异常类型：{}", error.getClass().getSimpleName());
        }
        boolean salesData = !map(analysis.get("marketingCurrent30Days")).isEmpty();
        boolean memberData = !map(analysis.get("memberSegments")).isEmpty();
        boolean inventoryData = analysis.containsKey("inventoryRisks");
        boolean hasScope = !scopeInfo.isEmpty();
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("salesOverview", modelReady && salesData);
        result.put("dishPerformance", modelReady && hasScope);
        result.put("storeComparison", modelReady && stores.size() > 1);
        result.put("memberAnalysis", modelReady && memberData);
        result.put("inventoryAnalysis", modelReady && inventoryData);
        result.put("marketingPrediction", false);
        return result;
    }

    /** 生成今日经营简报。 */
    public AiManagerResponse briefing(AiManagerRequest request) {
        assignCaller(request);
        return complete("BRIEFING", request, "DIAGNOSIS", "briefing", BriefingResult.class,
                AiManagerResultMapper::briefing, false, true);
    }

    /**
     * 提交 AI 经营诊断后台任务，立即返回请求ID；页面关闭后任务仍会继续生成并落库快照。
     * 不扣减调用额度，前端通过诊断记录接口轮询状态与结果。
     */
    public AiDashboardSubmission submitDashboard(AiManagerRequest request) {
        assignCaller(request);
        availability.requireModel();
        String scopeType = upper(request.getScopeType(), "TENANT");
        Long storeId = "STORE".equals(scopeType) ? request.getStoreId() : null;
        String timeRange = upper(request.getTimeRange(), "TODAY");
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BusinessException(400, "缺少 AI 请求ID");
        }
        try { UUID.fromString(request.getRequestId()); }
        catch (IllegalArgumentException error) { throw new BusinessException(400, "AI 请求ID必须是 UUID"); }
        request.setScopeType(scopeType);
        request.setStoreId(storeId);
        request.setTimeRange(timeRange);
        scopeGuard.validate(scopeType, storeId);

        // chargeQuota 传 false：仍做请求幂等记录，但不消耗 AI 调用次数，额度用尽也可继续诊断。
        AiGenerationDTO reservation = quota.reserve("DIAGNOSIS_DASHBOARD", request, false);
        if (!reservation.isAcquired()) {
            // 网络重试或并发复用同一请求时，直接返回已持久化的状态，由前端继续轮询。
            return new AiDashboardSubmission(request.getRequestId(), reservation.getStatus());
        }
        Long tenantId = TenantContext.getTenantId();
        boolean superAdmin = TenantContext.isSuperAdmin();
        boolean storeRestricted = StoreContext.isRestricted();
        List<Long> authorizedStores = StoreContext.getStoreIds();
        try {
            taskExecutor.execute(() -> runDashboard(tenantId, superAdmin, storeRestricted, authorizedStores, request));
        } catch (RejectedExecutionException error) {
            log.warn("AI 店长后台队列已满");
            quota.fail(request.getRequestId(), "QUEUE_FULL", "AI 经营诊断任务繁忙，请稍后重试", true);
            throw new BusinessException(503, "AI 经营诊断任务繁忙，请稍后重试");
        }
        return new AiDashboardSubmission(request.getRequestId(), "PROCESSING");
    }

    /** 查询当前范围和周期的经营诊断记录（含成功、失败与进行中），用于自动恢复与历史回看。 */
    public List<AiManagerRecord> diagnosisRecords(String scopeType, Long storeId, String timeRange) {
        String normalizedScope = upper(scopeType, "TENANT");
        String normalizedRange = upper(timeRange, "TODAY");
        scopeGuard.validate(normalizedScope, storeId);
        return quota.diagnosisRecords(normalizedScope, storeId, normalizedRange).stream().map(item -> {
            AiManagerResponse result = item.getResponseJson() == null || item.getResponseJson().isBlank()
                    ? null : objectMapper.readValue(item.getResponseJson(), AiManagerResponse.class);
            return new AiManagerRecord(item.getRequestId(), item.getOperation(), item.getStatus(),
                    item.getScopeType(), item.getStoreId(), item.getTimeRange(), item.getPeriodKey(),
                    item.getCreatedAt(), item.getUpdatedAt(), item.getFailureCode(), item.getFailureMessage(),
                    item.getRetryable(), result);
        }).toList();
    }

    /** 生成菜品经营建议。 */
    public AiManagerResponse dishAdvice(AiManagerRequest request) {
        assignCaller(request);
        return complete("DISH_ADVICE", request, "DISH", "dish-advice", DishAdviceResult.class,
                AiManagerResultMapper::dishAdvice, false, true);
    }

    /** 生成营销方案草稿，不执行发券或改价。 */
    public AiManagerResponse marketingDraft(AiManagerRequest request) {
        assignCaller(request);
        requireMessage(request, "请填写营销目标");
        return complete("MARKETING_DRAFT", request, "BRIEFING", "marketing-draft", MarketingDraftResult.class,
                AiManagerResultMapper::marketingDraft, false, true);
    }

    /** 回答基于当前经营范围的数据问题。指标由只读工具按问题获取。 */
    public AiAdvisorReply chat(AiManagerRequest request) {
        assignCaller(request);
        requireMessage(request, "请输入经营问题");
        availability.requireModel();
        String scopeType = upper(request.getScopeType(), "TENANT");
        scopeGuard.validate(scopeType, request.getStoreId());
        var session = workspace.openSession(request);
        request.setSessionId(session.getId());
        AiManagerResponse result = complete("CHAT", request, null, "chat", ChatAnswerResult.class,
                AiManagerResultMapper::chat, true, true);
        return new AiAdvisorReply(request.getRequestId(), session, result);
    }

    /** 提交经营简报和菜品建议后台任务，页面关闭后任务仍会继续。 */
    public AiInsightSubmission submitInsights(AiManagerRequest request) {
        assignCaller(request);
        availability.requireModel();
        String scopeType = upper(request.getScopeType(), "TENANT");
        Long storeId = request.getStoreId();
        String timeRange = upper(request.getTimeRange(), "TODAY");
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BusinessException(400, "缺少 AI 请求ID");
        }
        try { UUID.fromString(request.getRequestId()); }
        catch (IllegalArgumentException error) { throw new BusinessException(400, "AI 请求ID必须是 UUID"); }
        scopeGuard.validate(scopeType, storeId);

        long createdBy = request.getCreatedBy();
        Long tenantId = TenantContext.getTenantId();
        boolean superAdmin = TenantContext.isSuperAdmin();
        boolean storeRestricted = StoreContext.isRestricted();
        List<Long> authorizedStores = StoreContext.getStoreIds();
        AiManagerRequest briefingRequest = insightRequest(request, "BRIEFING", createdBy,
                scopeType, storeId, timeRange);
        AiManagerRequest dishRequest = insightRequest(request, "DISH_ADVICE", createdBy,
                scopeType, storeId, timeRange);
        AiGenerationDTO briefingReservation = quota.reserve("BRIEFING", briefingRequest);
        if (!briefingReservation.isAcquired()) {
            // 网络重试复用同一父请求时，直接返回已持久化的子任务ID。
            return new AiInsightSubmission(request.getRequestId(), briefingRequest.getRequestId(),
                    dishRequest.getRequestId(), "PROCESSING");
        }
        try {
            quota.reserve("DISH_ADVICE", dishRequest, false);
        } catch (RuntimeException error) {
            quota.fail(briefingRequest.getRequestId(), "SUBMISSION_FAILED",
                    "经营诊断子任务创建失败，本次额度已退回", true);
            throw error;
        }
        try {
            taskExecutor.execute(() -> runInsights(tenantId, superAdmin, storeRestricted, authorizedStores,
                    briefingRequest, dishRequest));
        } catch (RejectedExecutionException error) {
            log.warn("AI 店长后台队列已满");
            quota.fail(briefingRequest.getRequestId(), "QUEUE_FULL",
                    "AI 店长分析任务繁忙，本次额度已退回", true);
            quota.fail(dishRequest.getRequestId(), "QUEUE_FULL",
                    "AI 店长分析任务繁忙，请稍后重试", true);
            throw new BusinessException(503, "AI 店长分析任务繁忙，请稍后重试");
        }
        return new AiInsightSubmission(request.getRequestId(), briefingRequest.getRequestId(),
                dishRequest.getRequestId(), "PROCESSING");
    }

    /** 查询当前实际周期的分析状态和历史结果。 */
    public List<AiManagerRecord> records(String scopeType, Long storeId, String timeRange) {
        String normalizedScope = upper(scopeType, "TENANT");
        String normalizedRange = upper(timeRange, "TODAY");
        scopeGuard.validate(normalizedScope, storeId);
        return quota.records(normalizedScope, storeId, normalizedRange).stream().map(item -> {
            AiManagerResponse result = item.getResponseJson() == null || item.getResponseJson().isBlank()
                    ? null : objectMapper.readValue(item.getResponseJson(), AiManagerResponse.class);
            return new AiManagerRecord(item.getRequestId(), item.getOperation(), item.getStatus(),
                    item.getScopeType(), item.getStoreId(), item.getTimeRange(), item.getPeriodKey(),
                    item.getCreatedAt(), item.getUpdatedAt(), item.getFailureCode(), item.getFailureMessage(),
                    item.getRetryable(), result);
        }).toList();
    }

    private <T> AiManagerResponse complete(String type, AiManagerRequest request, String dataMode, String template,
                                           Class<T> resultType,
                                           BiFunction<T, Map<String, Object>, AiManagerResponse> mapper,
                                           boolean useTools, boolean chargeQuota) {
        return complete(type, request, dataMode, template, resultType, mapper, useTools, chargeQuota, false);
    }

    private <T> AiManagerResponse complete(String type, AiManagerRequest request, String dataMode, String template,
                                           Class<T> resultType,
                                           BiFunction<T, Map<String, Object>, AiManagerResponse> mapper,
                                           boolean useTools, boolean chargeQuota, boolean preReserved) {
        if (request.getCreatedBy() == null) throw new BusinessException(400, "缺少发起用户");
        ChatModel model = availability.requireModel();
        String scopeType = upper(request.getScopeType(), "TENANT");
        Long storeId = request.getStoreId();
        String timeRange = upper(request.getTimeRange(), "TODAY");
        scopeGuard.validate(scopeType, storeId);
        AiGenerationDTO reservation = preReserved ? new AiGenerationDTO().setAcquired(true)
                : chargeQuota ? quota.reserve(type, request) : quota.reserve(type, request, false);
        if (!reservation.isAcquired()) return quota.cached(reservation);
        try {
            Map<String, Object> source = useTools
                    ? chatScope(scopeType, storeId, timeRange)
                    : businessData.context(new AiManagerDataRequest(scopeType, storeId, timeRange, dataMode));
            Map<String, Object> businessContext = source;
            if (!useTools) {
                businessContext = new LinkedHashMap<>();
                businessContext.put("businessData", source);
                businessContext.put("metricCatalog", AiMetricCatalog.all());
            }
            String serialized = objectMapper.writeValueAsString(businessContext);
            if (serialized.length() > MAX_CONTEXT_CHARS) {
                throw new BusinessException(413, "经营数据过大，请缩小时间范围后重试");
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("businessContext", serialized);
            if ("marketing-draft".equals(template)) {
                variables.put("goal", request.getMessage().trim());
                variables.put("budget", request.getBudget() == null ? "未设置" : request.getBudget().toPlainString());
            }
            if ("chat".equals(template)) variables.put("question", request.getMessage().trim());
            ChatResponse modelResponse;
            T parsed;
            // 仪表盘结构复杂（嵌套枚举、图表、序列），严格转换器常失败；直接用宽松解析器从最终文本提取 JSON。
            boolean lenient = resultType == AiDashboardResult.class;
            try {
                String userMessage = prompts.user(template, variables)
                        + (lenient ? AiStructuredOutputParser.contract(resultType) : "");
                var spec = ChatClient.create(model).prompt()
                        .system(prompts.system())
                        .messages(historyMessages(request))
                        .user(userMessage);
                if (useTools) {
                    // 工具定义放进通用选项，避免普通 ChatOptions 覆盖掉回调；顾问负责限制轮次。
                    // 仪表盘 JSON 较长，放宽 token 上限避免输出被截断导致 JSON 不完整。
                    spec = spec.advisors(chatToolAdvisor)
                            .options(ToolCallingChatOptions.builder()
                                    .maxTokens(lenient ? 8192 : 4096)
                                    .toolCallbacks(ToolCallbacks.from(readTools))
                                    .toolContext(toolContext(scopeType, storeId, timeRange)));
                } else {
                    spec = spec.options(managerOptions());
                }
                if (lenient) {
                    modelResponse = spec.call().chatResponse();
                    parsed = AiStructuredOutputParser.parse(modelResponse, resultType);
                } else {
                    ResponseEntity<ChatResponse, T> entity = spec.call().responseEntity(resultType);
                    modelResponse = entity == null ? null : entity.response();
                    parsed = entity == null ? null : entity.entity();
                }
            } catch (RuntimeException error) {
                String reason = availability.classifyModelFailure(error);
                // 只记录异常类型，不记录经营数据、供应商正文或密钥。
                log.warn("AI 店长模型调用失败，分类：{}，异常链：{}",
                        reason, exceptionTypes(error));
                if ("INVALID_MODEL_RESPONSE".equals(reason) && !useTools) {
                    // 兼容偶发返回 Markdown 或截断 JSON 的模型，仅在无工具场景补偿一次，不重复扣租户额度。
                    try {
                        modelResponse = ChatClient.create(model).prompt()
                                .system(prompts.system())
                                .messages(historyMessages(request))
                                .user(prompts.user(template, variables) + AiStructuredOutputParser.contract(resultType))
                                .options(managerOptions())
                                .call().chatResponse();
                        parsed = AiStructuredOutputParser.parse(modelResponse, resultType);
                    } catch (RuntimeException retryError) {
                        retryError.addSuppressed(error);
                        log.warn("AI 店长结构化输出补偿重试失败，分类：{}，异常链：{}",
                                availability.classifyModelFailure(retryError), exceptionTypes(retryError));
                        throw availability.modelFailure(retryError);
                    }
                } else {
                    throw availability.modelFailure(error);
                }
            }
            Map<String, Object> scope = new LinkedHashMap<>();
            for (String key : List.of("scopeType", "storeId", "storeName", "timeRange",
                    "dataCutoffAt", "dataPeriods")) {
                if (source.containsKey(key)) scope.put(key, source.get(key));
            }
            AiManagerResponse result = mapper.apply(parsed, scope);
            quota.complete(request.getRequestId(), result, modelResponse);
            return result;
        } catch (RuntimeException error) {
            try {
                String failureCode = error instanceof BusinessException business
                        ? "BUSINESS_" + business.getCode() : "GENERATION_FAILED";
                String failureMessage = error instanceof BusinessException business
                        ? business.getMessage() : "AI 生成服务暂时不可用，请稍后重试";
                boolean retryable = !(error instanceof BusinessException business
                        && (business.getCode() == 400 || business.getCode() == 403));
                quota.fail(request.getRequestId(), failureCode, failureMessage, retryable);
            }
            catch (RuntimeException refundError) { error.addSuppressed(refundError); }
            throw error;
        }
    }

    /** 始终使用登录用户，忽略请求体里的 createdBy。后台任务在进入线程池前调用。 */
    private void assignCaller(AiManagerRequest request) {
        if (request == null) throw new BusinessException(400, "AI 请求不能为空");
        request.setCreatedBy(StpUtil.getLoginIdAsLong());
    }

    private Map<String, Object> chatScope(String scopeType, Long storeId, String timeRange) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("scopeType", scopeType);
        scope.put("storeId", storeId);
        scope.put("timeRange", timeRange);
        scope.put("note", "指标只能通过工具获取");
        scope.put("dataPeriods", "由经营问题决定；未指定时优先综合今日、近7天、本月与近30天菜品");
        return scope;
    }

    private Map<String, Object> toolContext(String scopeType, Long storeId, String timeRange) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("scopeType", scopeType);
        if (storeId != null) context.put("storeId", storeId);
        context.put("timeRange", timeRange);
        return context;
    }

    private List<Message> historyMessages(AiManagerRequest request) {
        if (request.getHistory() == null || request.getHistory().isEmpty()) return List.of();
        List<Message> messages = new ArrayList<>();
        for (AiManagerRequest.ConversationMessage item : request.getHistory()) {
            if (item == null || item.role() == null || item.content() == null || item.content().isBlank()) continue;
            if ("assistant".equals(item.role())) messages.add(new AssistantMessage(item.content()));
            else if ("user".equals(item.role())) messages.add(new UserMessage(item.content()));
        }
        return messages;
    }

    private String upper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 仅使用 Spring AI 通用选项，保持对所有 OpenAI-compatible 供应商的兼容性。
     * ChatClient.options 接收 Builder 而不是已经 build 的 ChatOptions。
     */
    private ChatOptions.Builder<?> managerOptions() {
        return ChatOptions.builder().maxTokens(4096);
    }

    private AiManagerRequest insightRequest(AiManagerRequest source, String operation, long createdBy,
                                            String scopeType, Long storeId, String timeRange) {
        AiManagerRequest target = new AiManagerRequest();
        target.setRequestId(UUID.nameUUIDFromBytes(
                (source.getRequestId() + ":" + operation).getBytes(StandardCharsets.UTF_8)).toString());
        target.setScopeType(scopeType);
        target.setStoreId("STORE".equals(scopeType) ? storeId : null);
        target.setTimeRange(timeRange);
        target.setCreatedBy(createdBy);
        return target;
    }

    /** 后台线程生成经营诊断仪表盘；设置好租户/门店上下文后复用已预留记录完成生成。 */
    private void runDashboard(Long tenantId, boolean superAdmin, boolean storeRestricted,
                             List<Long> authorizedStores, AiManagerRequest request) {
        try {
            TenantContext.setTenantId(tenantId);
            TenantContext.setSuperAdmin(superAdmin);
            StoreContext.setRestricted(storeRestricted, authorizedStores);
            // 后台线程没有登录上下文，直接完成已绑定发起人的请求；失败由 complete 内部落库。
            complete("DIAGNOSIS_DASHBOARD", request, null, "dashboard", AiDashboardResult.class,
                    AiManagerResultMapper::dashboard, true, false, true);
        } catch (RuntimeException error) {
            logBackgroundFailure("经营诊断仪表盘", request, error);
        } finally {
            StoreContext.clear();
            TenantContext.clear();
        }
    }

    private void runInsights(Long tenantId, boolean superAdmin, boolean storeRestricted,
                             List<Long> authorizedStores, AiManagerRequest briefingRequest,
                             AiManagerRequest dishRequest) {
        try {
            TenantContext.setTenantId(tenantId);
            TenantContext.setSuperAdmin(superAdmin);
            StoreContext.setRestricted(storeRestricted, authorizedStores);
            // 后台线程没有登录上下文，直接完成已绑定发起人的请求。
            try {
                complete("BRIEFING", briefingRequest, "DIAGNOSIS", "briefing", BriefingResult.class,
                        AiManagerResultMapper::briefing, false, true, true);
            } catch (RuntimeException error) {
                logBackgroundFailure("经营诊断", briefingRequest, error);
                // 主任务失败后不再执行菜品子任务，必须同步终止已预留记录，避免前端长期显示处理中。
                try {
                    quota.fail(dishRequest.getRequestId(), "PARENT_GENERATION_FAILED",
                            "经营诊断生成失败，菜品建议任务已取消，请重新发起", true);
                } catch (RuntimeException failError) {
                    error.addSuppressed(failError);
                    log.warn("AI 店长菜品建议取消状态保存失败，请求ID：{}，异常类型：{}",
                            dishRequest.getRequestId(), failError.getClass().getSimpleName());
                }
                return;
            }
            try { complete("DISH_ADVICE", dishRequest, "DISH", "dish-advice", DishAdviceResult.class,
                    AiManagerResultMapper::dishAdvice, false, false, true); }
            catch (RuntimeException error) { logBackgroundFailure("菜品建议", dishRequest, error); }
        } finally {
            StoreContext.clear();
            TenantContext.clear();
        }
    }

    private void logBackgroundFailure(String task, AiManagerRequest request, RuntimeException error) {
        if (error instanceof BusinessException business) {
            log.warn("AI 店长后台{}生成失败，请求ID：{}，错误码：{}，原因：{}",
                    task, request.getRequestId(), business.getCode(), business.getMessage());
            return;
        }
        log.warn("AI 店长后台{}生成失败，请求ID：{}，异常类型：{}",
                task, request.getRequestId(), error.getClass().getSimpleName());
    }

    /** 只拼接异常类名，既保留结构化输出故障线索，也不记录供应商正文和经营数据。 */
    private String exceptionTypes(Throwable error) {
        List<String> types = new ArrayList<>();
        for (Throwable cause = error; cause != null && types.size() < 8; cause = cause.getCause()) {
            String type = cause.getClass().getSimpleName();
            if (!types.contains(type)) types.add(type);
        }
        return String.join(" -> ", types);
    }

    private void requireMessage(AiManagerRequest request, String message) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException(400, message);
        }
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
