package org.hongtu.restaurant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.AiManagerDataRequest;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.VO.TenantAnalysisQuery;
import org.hongtu.restaurant.service.RestaurantAnalysisService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.StoreInfoService;
import org.hongtu.restaurant.service.TenantAnalysisService;
import org.hongtu.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 店长内部经营数据接口，只向 AI 服务暴露经过权限裁剪的结构化指标。
 */
@RestController
@RequestMapping("/inner/restaurant/ai-manager")
public class InnerAiManagerController {

    private final StoreInfoService storeInfoService;
    private final StoreDataScopeService storeDataScopeService;
    private final RestaurantAnalysisService restaurantAnalysisService;
    private final TenantAnalysisService tenantAnalysisService;

    public InnerAiManagerController(StoreInfoService storeInfoService,
                                    StoreDataScopeService storeDataScopeService,
                                    RestaurantAnalysisService restaurantAnalysisService,
                                    TenantAnalysisService tenantAnalysisService) {
        this.storeInfoService = storeInfoService;
        this.storeDataScopeService = storeDataScopeService;
        this.restaurantAnalysisService = restaurantAnalysisService;
        this.tenantAnalysisService = tenantAnalysisService;
    }

    /** 查询当前账号能够选择的租户或门店经营范围。 */
    @GetMapping("/scope")
    public Result<Map<String, Object>> scope() {
        // 平台全局模式不能越过租户隔离读取全部商户门店。
        TenantContext.getTenantId();
        boolean restricted = storeDataScopeService.requiresStoreRestriction();
        LambdaQueryWrapper<StoreInfo> query = new LambdaQueryWrapper<StoreInfo>()
                .select(StoreInfo::getId, StoreInfo::getStoreName, StoreInfo::getStatus)
                .orderByAsc(StoreInfo::getStoreName, StoreInfo::getId);
        if (restricted) {
            List<Long> allowedStoreIds = storeDataScopeService.getAllowedStoreIds();
            if (allowedStoreIds.isEmpty()) {
                return Result.success(Map.of(
                        "tenantViewAvailable", false,
                        "restricted", true,
                        "defaultScopeType", "STORE",
                        "stores", List.of()
                ));
            }
            query.in(StoreInfo::getId, allowedStoreIds);
        }
        List<Map<String, Object>> stores = storeInfoService.list(query).stream()
                .map(store -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("storeId", store.getId());
                    item.put("storeName", store.getStoreName());
                    item.put("status", store.getStatus());
                    return item;
                }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantViewAvailable", !restricted);
        result.put("restricted", restricted);
        result.put("defaultScopeType", restricted ? "STORE" : "TENANT");
        result.put("defaultStoreId", restricted && stores.size() == 1 ? stores.getFirst().get("storeId") : null);
        result.put("stores", stores);
        return Result.success(result);
    }

    /** 查询 AI 店长所需的销售、订单、门店和菜品经营指标。 */
    @PostMapping("/context")
    public Result<Map<String, Object>> context(@RequestBody AiManagerDataRequest request) {
        TenantContext.getTenantId();
        String scopeType = normalize(request == null ? null : request.scopeType(), "STORE");
        String timeRange = normalize(request == null ? null : request.timeRange(), "TODAY");
        String dataMode = normalize(request == null ? null : request.dataMode(), "FULL");
        resolveRange(timeRange);
        if (!List.of("BRIEFING", "DISH", "FULL", "DIAGNOSIS", "ANALYSIS").contains(dataMode)) {
            throw new IllegalArgumentException("不支持的 AI 店长数据模式");
        }
        if ("TENANT".equals(scopeType)) {
            if (storeDataScopeService.requiresStoreRestriction()) {
                throw new SecurityException("门店账号不能访问租户级 AI 店长");
            }
            if ("DIAGNOSIS".equals(dataMode)) return Result.success(tenantDiagnosisContext());
            if ("ANALYSIS".equals(dataMode)) return Result.success(analysisContext(
                    "TENANT", null, null));
            return Result.success(tenantContext(timeRange, dataMode));
        }
        if (!"STORE".equals(scopeType)) {
            throw new IllegalArgumentException("不支持的 AI 店长经营范围");
        }
        Long storeId = request == null ? null : request.storeId();
        if (storeId == null) {
            throw new IllegalArgumentException("请选择门店");
        }
        storeDataScopeService.checkStoreAccess(storeId);
        StoreInfo store = storeInfoService.getById(storeId);
        if (store == null) {
            throw new IllegalArgumentException("门店不存在或无权访问");
        }
        if ("DIAGNOSIS".equals(dataMode)) return Result.success(storeDiagnosisContext(store));
        if ("ANALYSIS".equals(dataMode)) return Result.success(analysisContext(
                "STORE", storeId, store.getStoreName()));
        return Result.success(storeContext(store, timeRange, dataMode));
    }

    /** 扩展经营分析只包含匿名聚合和有限风险样本。 */
    private Map<String, Object> analysisContext(String scopeType, Long storeId, String storeName) {
        Map<String, Object> result = new LinkedHashMap<>(
                restaurantAnalysisService.getAiExtendedAnalysisSnapshot(storeId));
        result.put("scopeType", scopeType);
        if (storeId != null) result.put("storeId", storeId);
        if (storeName != null) result.put("storeName", storeName);
        if ("TENANT".equals(scopeType)) {
            LocalDate today = LocalDate.now();
            TenantAnalysisQuery current = customTenantQuery(today.minusDays(6), today, 20);
            TenantAnalysisQuery previous = customTenantQuery(today.minusDays(13), today.minusDays(7), 20);
            result.put("storeBenchmarkCurrent7Days", tenantAnalysisService.storeRanking(current));
            result.put("storeBenchmarkPrevious7Days", tenantAnalysisService.storeRanking(previous));
        }
        return result;
    }

    /**
     * 总部经营诊断一次读取多个有界周期，避免用户先选择时间再分别生成碎片化结论。
     */
    private Map<String, Object> tenantDiagnosisContext() {
        LocalDate today = LocalDate.now();
        Map<String, Object> result = baseContext("TENANT", "AUTO");
        result.put("dataCutoffAt", java.time.LocalDateTime.now());
        result.put("dataPeriods", "今日、最近7天、本月至今、近30天菜品");
        result.put("today", Map.of(
                "overview", tenantAnalysisService.overview(customTenantQuery(today, today, 10))));
        TenantAnalysisQuery recent7Days = customTenantQuery(today.minusDays(6), today, 10);
        result.put("recent7Days", Map.of(
                "overview", tenantAnalysisService.overview(recent7Days),
                "storeRanking", tenantAnalysisService.storeRanking(recent7Days)));
        result.put("monthToDate", Map.of(
                "overview", tenantAnalysisService.overview(
                        customTenantQuery(today.withDayOfMonth(1), today, 10))));
        result.put("dishLast30Days", tenantAnalysisService.dishRanking(
                customTenantQuery(today.minusDays(29), today, 10)));
        return result;
    }

    /** 单店经营诊断按业务含义分别准备当日、短期、月度和菜品周期。 */
    private Map<String, Object> storeDiagnosisContext(StoreInfo store) {
        LocalDate today = LocalDate.now();
        Long storeId = store.getId();
        Map<String, Object> result = baseContext("STORE", "AUTO");
        result.put("storeId", storeId);
        result.put("storeName", store.getStoreName());
        result.put("dataCutoffAt", java.time.LocalDateTime.now());
        result.put("dataPeriods", "今日、最近7天、本月至今、近30天菜品");

        Map<String, Object> todayData = new LinkedHashMap<>();
        todayData.put("overview", restaurantAnalysisService.getSalesOverview(storeId, null, today, today));
        todayData.put("orderHealth", restaurantAnalysisService.getOrderAnalysis(storeId, null, today, today));
        todayData.put("cancelReasons", restaurantAnalysisService.getCancelReasonAnalysis(storeId, null, today, today));
        result.put("today", todayData);
        result.put("recent7Days", Map.of("overview", restaurantAnalysisService.getSalesOverview(
                storeId, null, today.minusDays(6), today)));
        result.put("monthToDate", Map.of("overview", restaurantAnalysisService.getSalesOverview(
                storeId, null, today.withDayOfMonth(1), today)));

        Map<String, Object> dishes = new LinkedHashMap<>();
        dishes.put("topSellingDishes", restaurantAnalysisService.getTopSellingDishes(
                storeId, 30, 10, null, null));
        dishes.put("slowMovingDishes", topSamples(restaurantAnalysisService.getSlowMovingDishes(
                storeId, 30, null, null)));
        dishes.put("refundDishStatistics", topSamples(restaurantAnalysisService.getDishChangeStatistics(
                storeId, 30, 2, null, null)));
        result.put("dishLast30Days", dishes);
        return result;
    }

    private Map<String, Object> tenantContext(String timeRange, String dataMode) {
        TenantAnalysisQuery current = tenantQuery(timeRange, 10);
        Map<String, Object> result = baseContext("TENANT", timeRange);
        result.put("dataPeriods", "DISH".equals(dataMode) ? "近30天菜品" : periodLabel(timeRange));
        if (!"DISH".equals(dataMode)) {
            result.put("overview", tenantAnalysisService.overview(current));
            result.put("storeRanking", tenantAnalysisService.storeRanking(current));
        }
        if (!"BRIEFING".equals(dataMode)) {
            TenantAnalysisQuery dishes = tenantQuery("CUSTOM", 10);
            dishes.setStartDate(LocalDate.now().minusDays(29));
            dishes.setEndDate(LocalDate.now());
            result.put("dishPortfolio", tenantAnalysisService.dishRanking(dishes));
        }
        return result;
    }

    private Map<String, Object> storeContext(StoreInfo store, String timeRange, String dataMode) {
        DateRange range = resolveRange(timeRange);
        Long storeId = store.getId();
        Map<String, Object> result = baseContext("STORE", timeRange);
        result.put("dataPeriods", "DISH".equals(dataMode) ? "近30天菜品" : periodLabel(timeRange));
        result.put("storeId", storeId);
        result.put("storeName", store.getStoreName());
        if (!"DISH".equals(dataMode)) {
            result.put("overview", restaurantAnalysisService.getSalesOverview(
                    storeId, null, range.startDate(), range.endDate()));
            result.put("orderHealth", restaurantAnalysisService.getOrderAnalysis(
                    storeId, null, range.startDate(), range.endDate()));
            result.put("cancelReasons", restaurantAnalysisService.getCancelReasonAnalysis(
                    storeId, null, range.startDate(), range.endDate()));
        }
        if (!"BRIEFING".equals(dataMode)) {
            // 菜品顾问使用近30天样本，避免单日低样本给出激进菜单建议。
            result.put("topSellingDishes", restaurantAnalysisService.getTopSellingDishes(
                    storeId, 30, 10, null, null));
            result.put("slowMovingDishes", topSamples(restaurantAnalysisService.getSlowMovingDishes(
                    storeId, 30, null, null)));
            result.put("refundDishStatistics", topSamples(restaurantAnalysisService.getDishChangeStatistics(
                    storeId, 30, 2, null, null)));
        }
        return result;
    }

    private Map<String, Object> baseContext(String scopeType, String timeRange) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeType", scopeType);
        result.put("timeRange", timeRange);
        result.put("generatedAt", java.time.LocalDateTime.now());
        result.put("dataCutoffAt", java.time.LocalDateTime.now());
        return result;
    }

    private TenantAnalysisQuery tenantQuery(String timeRange, int limit) {
        TenantAnalysisQuery query = new TenantAnalysisQuery();
        query.setTimeRange(timeRange);
        query.setScopeType("ALL");
        query.setLimit(limit);
        return query;
    }

    private TenantAnalysisQuery customTenantQuery(LocalDate startDate, LocalDate endDate, int limit) {
        TenantAnalysisQuery query = tenantQuery("CUSTOM", limit);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        return query;
    }

    private DateRange resolveRange(String timeRange) {
        LocalDate today = LocalDate.now();
        return switch (timeRange) {
            case "YESTERDAY" -> new DateRange(today.minusDays(1), today.minusDays(1));
            case "WEEK" -> new DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
            case "MONTH" -> new DateRange(today.withDayOfMonth(1), today);
            case "TODAY" -> new DateRange(today, today);
            default -> throw new IllegalArgumentException("不支持的时间范围");
        };
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private String periodLabel(String timeRange) {
        return switch (timeRange) {
            case "YESTERDAY" -> "昨日";
            case "WEEK" -> "本周至今";
            case "MONTH" -> "本月至今";
            default -> "今日";
        };
    }

    /** 限制送入模型的榜单样本数量，避免随门店菜单规模无限增长。 */
    private <T> List<T> topSamples(List<T> samples) {
        return samples == null ? List.of() : samples.stream().limit(10).toList();
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
