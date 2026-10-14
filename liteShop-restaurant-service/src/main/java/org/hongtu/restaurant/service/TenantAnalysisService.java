package org.hongtu.restaurant.service;

import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.TenantAnalysisMapper;
import org.hongtu.restaurant.VO.TenantAnalysisQuery;
import org.hongtu.restaurant.VO.analysis.TenantAnalysisVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 租户级经营分析服务。
 */
@Service
public class TenantAnalysisService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final TenantAnalysisMapper mapper;
    private final StoreDataScopeService storeDataScopeService;
    private final StoreGroupService storeGroupService;

    public TenantAnalysisService(TenantAnalysisMapper mapper,
                                 StoreDataScopeService storeDataScopeService,
                                 StoreGroupService storeGroupService) {
        this.mapper = mapper;
        this.storeDataScopeService = storeDataScopeService;
        this.storeGroupService = storeGroupService;
    }

    /** 查询租户核心经营总览。 */
    public TenantAnalysisVO.Overview overview(TenantAnalysisQuery query) {
        NormalizedQuery normalized = normalize(query);
        TenantAnalysisVO.Period period = normalized.period();
        Map<String, Object> row = mapper.selectOverview(period.getStartDate(), period.getEndDate(),
                period.getPreviousStartDate(), period.getPreviousEndDate(), normalized.groupId());
        int storeCount = integer(row, "selectedStoreCount");
        BigDecimal currentGross = decimal(row, "currentGrossSales");
        BigDecimal previousGross = decimal(row, "previousGrossSales");
        BigDecimal currentNet = decimal(row, "currentNetSales");
        BigDecimal previousNet = decimal(row, "previousNetSales");
        BigDecimal currentOrders = decimal(row, "currentOrderCount");
        BigDecimal previousOrders = decimal(row, "previousOrderCount");

        return new TenantAnalysisVO.Overview()
                .setPeriod(period)
                .setSelectedStoreCount(storeCount)
                .setGrossSales(comparison(currentGross, previousGross))
                .setNetSales(comparison(currentNet, previousNet))
                .setOrderCount(comparison(currentOrders, previousOrders))
                .setAvgOrderAmount(comparison(divide(currentNet, currentOrders),
                        divide(previousNet, previousOrders)))
                .setAvgStoreSales(comparison(divide(currentGross, BigDecimal.valueOf(storeCount)),
                        divide(previousGross, BigDecimal.valueOf(storeCount))))
                .setDataUpdatedAt(dateTime(row.get("dataUpdatedAt")));
    }

    /** 查询门店销售排行、增长最快和下降最明显门店。 */
    public TenantAnalysisVO.StoreRanking storeRanking(TenantAnalysisQuery query) {
        NormalizedQuery normalized = normalize(query);
        String metric = normalizeStoreMetric(query.getMetric());
        int limit = normalizeLimit(query.getLimit(), 5, 50);
        TenantAnalysisVO.Period period = normalized.period();
        List<TenantAnalysisVO.StoreRankItem> items = mapper.selectStoreMetrics(period.getStartDate(),
                        period.getEndDate(), period.getPreviousStartDate(), period.getPreviousEndDate(),
                        normalized.groupId()).stream()
                .map(row -> storeItem(row, metric))
                .toList();

        List<TenantAnalysisVO.StoreRankItem> sales = rank(items, Comparator
                .comparing(TenantAnalysisVO.StoreRankItem::getCurrentValue).reversed(), limit);
        List<TenantAnalysisVO.StoreRankItem> comparable = items.stream()
                .filter(item -> "NORMAL".equals(item.getGrowthStatus()))
                // 客单价在任一周期样本过小时不参与增长榜，避免偶然单影响结论。
                .filter(item -> !"AVG_ORDER".equals(metric) || item.getOrderCount() >= 10)
                .toList();
        List<TenantAnalysisVO.StoreRankItem> growthTop = rank(comparable, Comparator
                .comparing(TenantAnalysisVO.StoreRankItem::getGrowthRate,
                        Comparator.nullsLast(Comparator.reverseOrder())), limit);
        List<TenantAnalysisVO.StoreRankItem> growthBottom = rank(comparable, Comparator
                .comparing(TenantAnalysisVO.StoreRankItem::getGrowthRate,
                        Comparator.nullsLast(Comparator.naturalOrder())), limit);

        return new TenantAnalysisVO.StoreRanking().setMetric(metric).setPeriod(period)
                .setSalesRanking(sales).setGrowthTop(growthTop).setGrowthBottom(growthBottom);
    }

    /** 查询租户菜品排行、高毛利与滞销榜。 */
    public TenantAnalysisVO.DishRanking dishRanking(TenantAnalysisQuery query) {
        NormalizedQuery normalized = normalize(query);
        String metric = normalizeDishMetric(query.getMetric());
        int limit = normalizeLimit(query.getLimit(), 10, 100);
        TenantAnalysisVO.Period period = normalized.period();
        List<TenantAnalysisVO.DishRankItem> all = mapper.selectDishMetrics(period.getStartDate(),
                        period.getEndDate(), normalized.groupId()).stream()
                .map(this::dishItem)
                .toList();
        Comparator<TenantAnalysisVO.DishRankItem> rankingComparator = switch (metric) {
            case "NET_SALES" -> Comparator.comparing(TenantAnalysisVO.DishRankItem::getNetSales).reversed();
            case "PROFIT" -> Comparator.comparing(TenantAnalysisVO.DishRankItem::getGrossProfit,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(TenantAnalysisVO.DishRankItem::getSalesQuantity).reversed();
        };
        List<TenantAnalysisVO.DishRankItem> ranking = rankDish(all, rankingComparator, limit);
        List<TenantAnalysisVO.DishRankItem> highProfit = rankDish(all.stream()
                        .filter(item -> item.getGrossProfit() != null).toList(),
                Comparator.comparing(TenantAnalysisVO.DishRankItem::getGrossProfit).reversed(), 5);
        List<TenantAnalysisVO.DishRankItem> slow = rankDish(mapper.selectSlowMovingDishMetrics(
                        period.getStartDate(), period.getEndDate(), normalized.groupId(), 5).stream()
                .map(this::slowDishItem).toList(), Comparator
                .comparing(TenantAnalysisVO.DishRankItem::getSalesQuantity)
                .thenComparing(TenantAnalysisVO.DishRankItem::getNetSales), 5);
        return new TenantAnalysisVO.DishRanking().setMetric(metric).setPeriod(period)
                .setRanking(ranking).setHighProfit(highProfit).setSlowMoving(slow);
    }

    /** 查询门店和菜品横向对比矩阵。 */
    public TenantAnalysisVO.StoreDishMatrix storeDishMatrix(TenantAnalysisQuery query) {
        NormalizedQuery normalized = normalize(query);
        TenantAnalysisVO.Period period = normalized.period();
        int page = Math.max(1, query.getCurrentPage() == null ? 1 : query.getCurrentPage());
        int size = normalizeLimit(query.getPageSize(), 10, 30);
        List<TenantAnalysisVO.DishRankItem> dishes = mapper.selectDishMetrics(period.getStartDate(),
                        period.getEndDate(), normalized.groupId()).stream().map(this::dishItem)
                .sorted(Comparator.comparing(TenantAnalysisVO.DishRankItem::getNetSales).reversed())
                .toList();
        int from = Math.min((page - 1) * size, dishes.size());
        int to = Math.min(from + size, dishes.size());
        List<TenantAnalysisVO.DishRankItem> selectedDishes = dishes.subList(from, to);
        int totalStoreCount = mapper.countMatrixStores(normalized.groupId());
        List<TenantAnalysisVO.MatrixStore> stores = mapper.selectMatrixStores(normalized.groupId(), 30)
                .stream().map(row -> new TenantAnalysisVO.MatrixStore()
                        .setStoreId(longValue(row, "storeId")).setStoreName(text(row, "storeName"))).toList();

        Map<String, Map<String, Object>> rawCells = new HashMap<>();
        if (!selectedDishes.isEmpty()) {
            mapper.selectMatrixCells(period.getStartDate(), period.getEndDate(), normalized.groupId(),
                            selectedDishes.stream().map(TenantAnalysisVO.DishRankItem::getDishId).toList())
                    .forEach(row -> rawCells.put(cellKey(longValue(row, "dishId"), longValue(row, "storeId")), row));
        }

        List<TenantAnalysisVO.MatrixRow> rows = new ArrayList<>();
        for (TenantAnalysisVO.DishRankItem dish : selectedDishes) {
            List<TenantAnalysisVO.MatrixCell> cells = stores.stream()
                    .map(store -> matrixCell(store.getStoreId(), rawCells.get(cellKey(dish.getDishId(), store.getStoreId()))))
                    .toList();
            BigDecimal averagePenetration = cells.stream().filter(cell -> Boolean.TRUE.equals(cell.getListed()))
                    .map(TenantAnalysisVO.MatrixCell::getOrderPenetrationRate)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long listedCount = cells.stream().filter(cell -> Boolean.TRUE.equals(cell.getListed())).count();
            averagePenetration = divide(averagePenetration, BigDecimal.valueOf(listedCount));
            for (TenantAnalysisVO.MatrixCell cell : cells) {
                if (Boolean.TRUE.equals(cell.getListed()) && averagePenetration.signum() > 0) {
                    cell.setPerformanceIndex(cell.getOrderPenetrationRate().multiply(HUNDRED)
                            .divide(averagePenetration, 2, RoundingMode.HALF_UP));
                }
            }
            rows.add(new TenantAnalysisVO.MatrixRow().setDishId(dish.getDishId())
                    .setDishName(dish.getDishName()).setCategoryName(dish.getCategoryName()).setCells(cells));
        }
        return new TenantAnalysisVO.StoreDishMatrix().setPeriod(period).setCurrentPage(page)
                .setPageSize(size).setTotal(dishes.size()).setTotalStoreCount(totalStoreCount)
                .setStores(stores).setRows(rows);
    }

    /** 查询选定菜品在当前门店范围内的横向经营表现。 */
    public TenantAnalysisVO.StoreDishComparison storeDishComparison(TenantAnalysisQuery query) {
        NormalizedQuery normalized = normalize(query);
        TenantAnalysisVO.Period period = normalized.period();
        List<TenantAnalysisVO.ComparisonDish> dishes = mapper.selectComparisonDishes(normalized.groupId())
                .stream().map(row -> new TenantAnalysisVO.ComparisonDish()
                        .setDishId(longValue(row, "dishId"))
                        .setDishName(text(row, "dishName"))
                        .setCategoryName(text(row, "categoryName")))
                .toList();
        TenantAnalysisVO.StoreDishComparison result = new TenantAnalysisVO.StoreDishComparison()
                .setPeriod(period).setDishes(dishes);
        if (dishes.isEmpty()) return result;

        Long selectedDishId = query.getDishId() == null ? dishes.getFirst().getDishId() : query.getDishId();
        TenantAnalysisVO.ComparisonDish selectedDish = dishes.stream()
                .filter(dish -> Objects.equals(dish.getDishId(), selectedDishId))
                .findFirst().orElseThrow(() -> new BusinessException("所选菜品不在当前门店范围内"));
        List<TenantAnalysisVO.StoreDishComparisonItem> stores = mapper.selectDishStoreComparison(
                        period.getStartDate(), period.getEndDate(), normalized.groupId(), selectedDishId)
                .stream().map(row -> {
                    BigDecimal netSales = decimal(row, "netSales");
                    BigDecimal storeNetSales = decimal(row, "storeNetSales");
                    return new TenantAnalysisVO.StoreDishComparisonItem()
                            .setStoreId(longValue(row, "storeId"))
                            .setStoreName(text(row, "storeName"))
                            .setListed(integer(row, "listed") == 1)
                            .setSalesQuantity(integer(row, "salesQuantity"))
                            .setNetSales(netSales)
                            .setStoreNetSales(storeNetSales)
                            .setSalesShareRate(divide(netSales, storeNetSales).multiply(HUNDRED));
                }).sorted(Comparator.comparing(TenantAnalysisVO.StoreDishComparisonItem::getNetSales).reversed()
                        .thenComparing(TenantAnalysisVO.StoreDishComparisonItem::getSalesQuantity,
                                Comparator.reverseOrder())
                        .thenComparing(TenantAnalysisVO.StoreDishComparisonItem::getStoreName))
                .toList();
        for (int index = 0; index < stores.size(); index++) stores.get(index).setRanking(index + 1);
        return result.setSelectedDishId(selectedDish.getDishId())
                .setSelectedDishName(selectedDish.getDishName())
                .setSelectedCategoryName(selectedDish.getCategoryName())
                .setStores(stores);
    }

    private NormalizedQuery normalize(TenantAnalysisQuery query) {
        if (query == null) query = new TenantAnalysisQuery();
        if (storeDataScopeService.requiresStoreRestriction()) {
            throw new BusinessException("租户经营分析仅限总部账号访问");
        }
        String scope = upper(query.getScopeType(), "ALL");
        Long groupId = null;
        if ("GROUP".equals(scope)) {
            if (query.getGroupId() == null) throw new BusinessException("请选择门店分组");
            // 同时完成当前租户归属、启用状态和总部权限校验。
            storeGroupService.resolveEnabledGroupStores(query.getGroupId());
            groupId = query.getGroupId();
        } else if (!"ALL".equals(scope)) {
            throw new BusinessException("不支持的门店范围");
        }
        return new NormalizedQuery(resolvePeriod(query), groupId);
    }

    private TenantAnalysisVO.Period resolvePeriod(TenantAnalysisQuery query) {
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end;
        LocalDate previousStart;
        LocalDate previousEnd;
        String label;
        String range = upper(query.getTimeRange(), "TODAY");
        switch (range) {
            case "YESTERDAY" -> {
                start = end = today.minusDays(1);
                previousStart = previousEnd = start.minusDays(1);
                label = "较前日";
            }
            case "WEEK" -> {
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = today;
                previousStart = start.minusWeeks(1);
                previousEnd = end.minusWeeks(1);
                label = "较上周同期";
            }
            case "MONTH" -> {
                start = today.withDayOfMonth(1);
                end = today;
                previousStart = start.minusMonths(1);
                previousEnd = end.minusMonths(1);
                label = "较上月同期";
            }
            case "YEAR" -> {
                start = today.withDayOfYear(1);
                end = today;
                previousStart = start.minusYears(1);
                previousEnd = end.minusYears(1);
                label = "较去年同期";
            }
            case "CUSTOM" -> {
                start = query.getStartDate();
                end = query.getEndDate();
                if (start == null || end == null) throw new BusinessException("请选择自定义日期范围");
                long customDays = ChronoUnit.DAYS.between(start, end) + 1;
                previousEnd = start.minusDays(1);
                previousStart = previousEnd.minusDays(customDays - 1);
                label = "较上期";
            }
            case "TODAY" -> {
                start = end = today;
                previousStart = previousEnd = today.minusDays(1);
                label = "较昨日";
            }
            default -> throw new BusinessException("不支持的时间范围");
        }
        if (start.isAfter(end) || end.isAfter(today)) throw new BusinessException("日期范围不正确");
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 366) throw new BusinessException("日期范围不能超过366天");
        return new TenantAnalysisVO.Period().setStartDate(start).setEndDate(end)
                .setPreviousStartDate(previousStart).setPreviousEndDate(previousEnd)
                .setComparisonLabel(label);
    }

    private TenantAnalysisVO.StoreRankItem storeItem(Map<String, Object> row, String metric) {
        BigDecimal currentGross = decimal(row, "currentGrossSales");
        BigDecimal previousGross = decimal(row, "previousGrossSales");
        BigDecimal currentNet = decimal(row, "currentNetSales");
        BigDecimal previousNet = decimal(row, "previousNetSales");
        BigDecimal currentOrders = decimal(row, "currentOrderCount");
        BigDecimal previousOrders = decimal(row, "previousOrderCount");
        BigDecimal current = switch (metric) {
            case "NET_SALES" -> currentNet;
            case "ORDER_COUNT" -> currentOrders;
            case "AVG_ORDER" -> divide(currentNet, currentOrders);
            default -> currentGross;
        };
        BigDecimal previous = switch (metric) {
            case "NET_SALES" -> previousNet;
            case "ORDER_COUNT" -> previousOrders;
            case "AVG_ORDER" -> divide(previousNet, previousOrders);
            default -> previousGross;
        };
        TenantAnalysisVO.MetricComparison comparison = comparison(current, previous);
        return new TenantAnalysisVO.StoreRankItem().setStoreId(longValue(row, "storeId"))
                .setStoreName(text(row, "storeName")).setCurrentValue(current).setPreviousValue(previous)
                .setChangeAmount(comparison.getChangeAmount()).setGrowthRate(comparison.getGrowthRate())
                .setGrowthStatus(comparison.getGrowthStatus()).setGrossSales(currentGross).setNetSales(currentNet)
                .setOrderCount(currentOrders.longValue()).setAvgOrderAmount(divide(currentNet, currentOrders));
    }

    private TenantAnalysisVO.DishRankItem dishItem(Map<String, Object> row) {
        int quantity = integer(row, "salesQuantity");
        int giftQuantity = integer(row, "giftQuantity");
        int unknown = integer(row, "unknownCostQuantity");
        BigDecimal netSales = decimal(row, "netSales");
        BigDecimal grossProfit = nullableDecimal(row, "grossProfit");
        int totalCostedQuantity = quantity + giftQuantity;
        BigDecimal completeness = totalCostedQuantity <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.max(0, totalCostedQuantity - unknown)).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalCostedQuantity), 2, RoundingMode.HALF_UP);
        return new TenantAnalysisVO.DishRankItem().setDishId(longValue(row, "dishId"))
                .setDishName(text(row, "dishName")).setCategoryName(text(row, "categoryName"))
                .setSalesQuantity(quantity).setGrossSales(decimal(row, "grossSales")).setNetSales(netSales)
                .setCostAmount(nullableDecimal(row, "costAmount")).setGrossProfit(grossProfit)
                .setGrossMarginRate(grossProfit == null || netSales.signum() == 0 ? null
                        : grossProfit.multiply(HUNDRED).divide(netSales, 2, RoundingMode.HALF_UP))
                .setOrderCount(decimal(row, "orderCount").longValue()).setGiftQuantity(giftQuantity)
                .setUnknownCostQuantity(unknown).setCostCompletenessRate(completeness)
                .setLastSaleDate(localDate(row.get("lastSaleDate")));
    }

    private TenantAnalysisVO.DishRankItem slowDishItem(Map<String, Object> row) {
        LocalDate lastSale = localDate(row.get("lastSaleDate"));
        return new TenantAnalysisVO.DishRankItem().setDishId(longValue(row, "dishId"))
                .setDishName(text(row, "dishName")).setCategoryName(text(row, "categoryName"))
                .setSalesQuantity(integer(row, "salesQuantity")).setNetSales(decimal(row, "netSales"))
                .setLastSaleDate(lastSale).setInactiveDays(lastSale == null ? null
                        : Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(lastSale, LocalDate.now()))));
    }

    private TenantAnalysisVO.MatrixCell matrixCell(Long storeId, Map<String, Object> row) {
        if (row == null) return new TenantAnalysisVO.MatrixCell().setStoreId(storeId).setListed(false);
        BigDecimal storeOrders = decimal(row, "storeOrderCount");
        BigDecimal penetration = divide(decimal(row, "dishOrderCount"), storeOrders).multiply(HUNDRED);
        return new TenantAnalysisVO.MatrixCell().setStoreId(storeId).setListed(integer(row, "listed") == 1)
                .setSalesQuantity(integer(row, "salesQuantity")).setNetSales(decimal(row, "netSales"))
                .setGrossProfit(nullableDecimal(row, "grossProfit")).setOrderPenetrationRate(penetration);
    }

    private <T> List<TenantAnalysisVO.StoreRankItem> rank(List<TenantAnalysisVO.StoreRankItem> source,
                                                           Comparator<TenantAnalysisVO.StoreRankItem> comparator,
                                                           int limit) {
        List<TenantAnalysisVO.StoreRankItem> result = source.stream().sorted(comparator).limit(limit)
                .map(this::copyStoreItem).toList();
        for (int i = 0; i < result.size(); i++) result.get(i).setRanking(i + 1);
        return result;
    }

    private List<TenantAnalysisVO.DishRankItem> rankDish(List<TenantAnalysisVO.DishRankItem> source,
                                                          Comparator<TenantAnalysisVO.DishRankItem> comparator,
                                                          int limit) {
        // 同一个菜品会同时出现在总榜和高毛利榜中，复制后再写排名，避免两个榜单相互覆盖 ranking。
        List<TenantAnalysisVO.DishRankItem> result = source.stream().sorted(comparator).limit(limit)
                .map(this::copyDishItem).toList();
        for (int i = 0; i < result.size(); i++) result.get(i).setRanking(i + 1);
        return result;
    }

    private TenantAnalysisVO.DishRankItem copyDishItem(TenantAnalysisVO.DishRankItem item) {
        return new TenantAnalysisVO.DishRankItem().setDishId(item.getDishId()).setDishName(item.getDishName())
                .setCategoryName(item.getCategoryName()).setSalesQuantity(item.getSalesQuantity())
                .setGrossSales(item.getGrossSales()).setNetSales(item.getNetSales())
                .setCostAmount(item.getCostAmount()).setGrossProfit(item.getGrossProfit())
                .setGrossMarginRate(item.getGrossMarginRate()).setOrderCount(item.getOrderCount())
                .setGiftQuantity(item.getGiftQuantity()).setUnknownCostQuantity(item.getUnknownCostQuantity())
                .setCostCompletenessRate(item.getCostCompletenessRate()).setLastSaleDate(item.getLastSaleDate())
                .setInactiveDays(item.getInactiveDays());
    }

    private TenantAnalysisVO.StoreRankItem copyStoreItem(TenantAnalysisVO.StoreRankItem item) {
        return new TenantAnalysisVO.StoreRankItem().setStoreId(item.getStoreId()).setStoreName(item.getStoreName())
                .setCurrentValue(item.getCurrentValue()).setPreviousValue(item.getPreviousValue())
                .setChangeAmount(item.getChangeAmount()).setGrowthRate(item.getGrowthRate())
                .setGrowthStatus(item.getGrowthStatus()).setGrossSales(item.getGrossSales())
                .setNetSales(item.getNetSales()).setOrderCount(item.getOrderCount())
                .setAvgOrderAmount(item.getAvgOrderAmount());
    }

    private TenantAnalysisVO.MetricComparison comparison(BigDecimal current, BigDecimal previous) {
        TenantAnalysisVO.MetricComparison result = new TenantAnalysisVO.MetricComparison()
                .setCurrentValue(current).setPreviousValue(previous).setChangeAmount(current.subtract(previous));
        if (previous.signum() == 0) {
            return result.setGrowthRate(current.signum() == 0 ? BigDecimal.ZERO : null)
                    .setGrowthStatus(current.signum() == 0 ? "UNCHANGED" : "NEW");
        }
        return result.setGrowthRate(current.subtract(previous).multiply(HUNDRED)
                        .divide(previous, 2, RoundingMode.HALF_UP)).setGrowthStatus("NORMAL");
    }

    private String normalizeStoreMetric(String metric) {
        String value = upper(metric, "GROSS_SALES");
        return List.of("GROSS_SALES", "NET_SALES", "ORDER_COUNT", "AVG_ORDER").contains(value)
                ? value : "GROSS_SALES";
    }

    private String normalizeDishMetric(String metric) {
        String value = upper(metric, "QUANTITY");
        return List.of("QUANTITY", "NET_SALES", "PROFIT").contains(value) ? value : "QUANTITY";
    }

    private int normalizeLimit(Integer value, int defaultValue, int maximum) {
        return Math.min(maximum, Math.max(1, value == null ? defaultValue : value));
    }

    private String upper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return denominator == null || denominator.signum() == 0 ? BigDecimal.ZERO
                : numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        BigDecimal value = nullableDecimal(row, key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nullableDecimal(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value == null ? null : new BigDecimal(value.toString());
    }

    private int integer(Map<String, Object> row, String key) {
        return decimal(row, key).intValue();
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value == null ? null : Long.valueOf(value.toString());
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value == null ? "" : value.toString();
    }

    private LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    private java.time.LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return java.time.LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private String cellKey(Long dishId, Long storeId) {
        return dishId + ":" + storeId;
    }

    private record NormalizedQuery(TenantAnalysisVO.Period period, Long groupId) {
    }
}
