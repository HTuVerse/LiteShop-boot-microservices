package org.hongtu.ai.tool;

import org.hongtu.ai.application.AiManagerScopeGuard;
import org.hongtu.ai.application.AiMetricCatalog;
import org.hongtu.ai.client.AiBusinessDataClient;
import org.hongtu.api.dto.AiManagerDataRequest;
import org.hongtu.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 店长经营追问的只读工具。不提供发券、改价、上下架或 SQL，每次调用都重新校验门店权限。
 */
@Component
public class AiManagerReadTools {
    private static final Logger log = LoggerFactory.getLogger(AiManagerReadTools.class);
    /** 预留统一指标目录空间；仍限制单次工具结果，避免整表数据进入模型。 */
    static final int MAX_RESULT_CHARS = 12000;
    private static final int MAX_STORES = 50;
    private static final List<String> TIME_RANGES = List.of("TODAY", "YESTERDAY", "WEEK", "MONTH");

    private final AiBusinessDataClient businessData;
    private final AiManagerScopeGuard scopeGuard;
    private final ObjectMapper objectMapper;
    public AiManagerReadTools(AiBusinessDataClient businessData, AiManagerScopeGuard scopeGuard,
                              ObjectMapper objectMapper) {
        this.businessData = businessData;
        this.scopeGuard = scopeGuard;
        this.objectMapper = objectMapper;
    }

    /** 查询当前授权范围内的销售与订单概况。 */
    @Tool(name = "getSalesOverview", description = "查询当前授权范围内的销售与订单概况。时间范围只能是 TODAY、YESTERDAY、WEEK、MONTH。不返回会员姓名或手机号。")
    public String getSalesOverview(
            @ToolParam(description = "时间范围：TODAY、YESTERDAY、WEEK、MONTH。不传则使用当前对话范围", required = false)
            String timeRange,
            @ToolParam(description = "门店ID。单店对话不要改传其他门店；租户对话可传已授权门店ID", required = false)
            Long storeId,
            ToolContext toolContext) {
        return guarded("getSalesOverview", () -> metrics("BRIEFING", timeRange, storeId, toolContext));
    }

    /** 查询近30天热销、滞销和退菜样本，条数由餐饮服务限制。 */
    @Tool(name = "getDishPerformance", description = "查询近30天热销、滞销和退菜样本。不能用它修改菜品。")
    public String getDishPerformance(
            @ToolParam(description = "门店ID。单店对话不要改传其他门店；租户对话可传已授权门店ID", required = false)
            Long storeId,
            ToolContext toolContext) {
        return guarded("getDishPerformance", () -> metrics("DISH", null, storeId, toolContext));
    }

    /** 自动查询当日、近7天、本月至今和近30天菜品的综合经营快照。 */
    @Tool(name = "getOperatingDiagnosis", description = "查询综合经营快照，包含今日、最近7天、本月至今和近30天菜品。用户询问整体经营情况、原因或应采取的动作时优先使用。")
    public String getOperatingDiagnosis(
            @ToolParam(description = "门店ID。单店对话不要改传其他门店；租户对话可传已授权门店ID", required = false)
            Long storeId,
            ToolContext toolContext) {
        return guarded("getOperatingDiagnosis", () -> metrics("DIAGNOSIS", null, storeId, toolContext));
    }

    /** 只查询匿名会员分群，避免为一个会员问题携带整份经营快照。 */
    @Tool(name = "getMemberAnalysis", description = "查询匿名会员分群、复购率和沉睡率。只返回聚合数据，不返回会员姓名、手机号或用户ID。")
    public String getMemberAnalysis(
            @ToolParam(description = "门店ID。单店对话不要改传其他门店；租户对话可传已授权门店ID", required = false)
            Long storeId,
            ToolContext toolContext) {
        return guarded("getMemberAnalysis", () -> metricsProjection("ANALYSIS", null, storeId, toolContext,
                "memberSegments", "memberInsight", "dataGaps"));
    }

    /** 只查询库存风险。 */
    @Tool(name = "getInventoryRisks", description = "查询低库存和售罄菜品风险，适合回答缺货、备货和库存异常问题。不能修改库存。")
    public String getInventoryRisks(
            @ToolParam(description = "门店ID。单店对话不要改传其他门店；租户对话可传已授权门店ID", required = false)
            Long storeId,
            ToolContext toolContext) {
        return guarded("getInventoryRisks", () -> metricsProjection("ANALYSIS", null, storeId, toolContext,
                "inventoryRisks", "dataGaps"));
    }

    /** 只查询订单健康度与退菜损耗代理。 */
    @Tool(name = "getOrderHealth", description = "查询订单取消率、取消原因和退菜损耗代理，适合回答订单异常、取消和退菜问题。")
    public String getOrderHealth(
            @ToolParam(description = "门店ID。单店对话不要改传其他门店；租户对话可传已授权门店ID", required = false)
            Long storeId,
            ToolContext toolContext) {
        return guarded("getOrderHealth", () -> metricsProjection("ANALYSIS", null, storeId, toolContext,
                "orderHealth30Days", "wasteProxy", "dataGaps"));
    }

    /** 只查询营销效率的前后周期对比。 */
    @Tool(name = "getMarketingEfficiency", description = "查询最近30天与前30天的销售、订单和优惠成本率，适合判断活动是否有效。")
    public String getMarketingEfficiency(
            @ToolParam(description = "门店ID。单店对话不要改传其他门店；租户对话可传已授权门店ID", required = false)
            Long storeId,
            ToolContext toolContext) {
        return guarded("getMarketingEfficiency", () -> metricsProjection("ANALYSIS", null, storeId, toolContext,
                "marketingCurrent30Days", "marketingPrevious30Days", "dataGaps"));
    }

    /** 查询租户范围的门店对标；单店范围不会越权读取其他门店。 */
    @Tool(name = "getStoreBenchmark", description = "查询当前租户授权范围内近7天门店经营对标及相邻周期变化。单店对话只能得到当前门店数据。")
    public String getStoreBenchmark(ToolContext toolContext) {
        return guarded("getStoreBenchmark", () -> metricsProjection("ANALYSIS", null, null, toolContext,
                "storeBenchmarkCurrent7Days", "storeBenchmarkPrevious7Days", "dataGaps"));
    }

    /** 列出当前账号可选门店，只含门店 ID、名称和状态。 */
    @Tool(name = "listAuthorizedStores", description = "列出当前账号可以分析的门店。不能用返回结果推断未授权门店。")
    public String listAuthorizedStores(ToolContext toolContext) {
        return guarded("listAuthorizedStores", () -> {
            ResolvedScope scope = resolve(toolContext, null, null);
            scopeGuard.validate(scope.scopeType(), scope.storeId());
            Object values = scopeGuard.scope().get("stores");
            List<Map<String, Object>> stores = new ArrayList<>();
            if (values instanceof List<?> items) {
                for (Object item : items) {
                    if (stores.size() >= MAX_STORES || !(item instanceof Map<?, ?> store)) continue;
                    Map<String, Object> copy = new LinkedHashMap<>();
                    copy.put("storeId", store.get("storeId"));
                    copy.put("storeName", store.get("storeName"));
                    copy.put("status", store.get("status"));
                    stores.add(copy);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stores", stores);
            result.put("truncated", values instanceof List<?> items && items.size() > stores.size());
            return bound(objectMapper.writeValueAsString(result));
        });
    }

    private String metrics(String dataMode, String requestedTime, Long requestedStoreId, ToolContext toolContext) {
        ResolvedScope scope = resolve(toolContext, requestedStoreId, requestedTime);
        scopeGuard.validate(scope.scopeType(), scope.storeId());
        Map<String, Object> context = businessData.context(
                new AiManagerDataRequest(scope.scopeType(), scope.storeId(), scope.timeRange(), dataMode));
        Map<String, Object> result = new LinkedHashMap<>(context);
        result.put("metricCatalog", AiMetricCatalog.all());
        return bound(objectMapper.writeValueAsString(result));
    }

    /**
     * 细粒度工具只保留回答当前问题需要的字段，同时携带范围、周期和统一指标口径。
     */
    private String metricsProjection(String dataMode, String requestedTime, Long requestedStoreId,
                                     ToolContext toolContext, String... fields) {
        ResolvedScope scope = resolve(toolContext, requestedStoreId, requestedTime);
        scopeGuard.validate(scope.scopeType(), scope.storeId());
        Map<String, Object> context = businessData.context(
                new AiManagerDataRequest(scope.scopeType(), scope.storeId(), scope.timeRange(), dataMode));
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of("scopeType", "storeId", "storeName", "timeRange", "dataPeriods",
                "dataCutoffAt", "generatedAt")) {
            if (context.containsKey(field)) result.put(field, context.get(field));
        }
        for (String field : fields) {
            if (context.containsKey(field)) result.put(field, context.get(field));
        }
        result.put("metricCatalog", AiMetricCatalog.all());
        return bound(objectMapper.writeValueAsString(result));
    }

    /**
     * 单店对话锁定当前门店；租户对话只允许跳到授权列表中的门店。时间范围只接受固定枚举。
     */
    private ResolvedScope resolve(ToolContext toolContext, Long requestedStoreId, String requestedTime) {
        Map<String, Object> context = toolContext == null || toolContext.getContext() == null
                ? Map.of() : toolContext.getContext();
        String scopeType = upper(text(context.get("scopeType")), "TENANT");
        Long scopeStoreId = longValue(context.get("storeId"));
        String fallbackTime = upper(text(context.get("timeRange")), "TODAY");
        String timeRange = requestedTime == null || requestedTime.isBlank() ? fallbackTime : upper(requestedTime, fallbackTime);
        if (!TIME_RANGES.contains(timeRange)) {
            throw new BusinessException(400, "不支持的时间范围");
        }
        if ("STORE".equals(scopeType)) {
            if (scopeStoreId == null) throw new BusinessException(400, "请选择要分析的门店");
            if (requestedStoreId != null && !requestedStoreId.equals(scopeStoreId)) {
                throw new BusinessException(403, "当前账号无权查看该门店分析");
            }
            return new ResolvedScope("STORE", scopeStoreId, timeRange);
        }
        if (!"TENANT".equals(scopeType)) throw new BusinessException(400, "不支持的经营范围");
        if (requestedStoreId != null) return new ResolvedScope("STORE", requestedStoreId, timeRange);
        return new ResolvedScope("TENANT", null, timeRange);
    }

    private String guarded(String toolName, ToolAction action) {
        try {
            return action.get();
        } catch (BusinessException error) {
            // 只记工具名和错误码，不记参数、经营数据或异常正文。
            log.warn("AI 店长工具拒绝，工具：{}，错误码：{}", toolName, error.getCode());
            return "工具未返回数据：" + error.getMessage();
        } catch (RuntimeException error) {
            log.warn("AI 店长工具失败，工具：{}，异常类型：{}", toolName, error.getClass().getSimpleName());
            return "工具暂时不可用。请说明数据不足，不得编造指标。";
        }
    }

    private String bound(String json) {
        if (json != null && json.length() > MAX_RESULT_CHARS) {
            return "数据量超出单次工具返回上限，请缩小范围后再问。";
        }
        return json == null ? "" : json;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new BusinessException(400, "门店ID不正确");
        }
    }

    private String upper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ResolvedScope(String scopeType, Long storeId, String timeRange) {
    }

    @FunctionalInterface
    private interface ToolAction {
        String get();
    }
}
