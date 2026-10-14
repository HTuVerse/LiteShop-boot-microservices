package org.hongtu.restaurant.service.impl;

import jakarta.annotation.Resource;
import org.hongtu.restaurant.DAO.RestaurantAnalysisMapper;
import org.hongtu.restaurant.VO.analysis.*;
import org.hongtu.restaurant.service.RestaurantAnalysisService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 餐厅数据分析服务实现类
 *
 * @author Ht
 * @since 2025-01-30
 */
@Service
public class RestaurantAnalysisServiceImpl implements RestaurantAnalysisService {

    @Resource
    private RestaurantAnalysisMapper analysisMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 聚合最近经营闭环指标，不加载会员明细或无界订单集合。 */
    @Override
    public Map<String, Object> getAiExtendedAnalysisSnapshot(Long storeId) {
        LocalDate end = LocalDate.now();
        LocalDate start30 = end.minusDays(29);
        LocalDate previousStart = start30.minusDays(30);
        int lowStock = 10;
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("memberSegments", analysisMapper.getAiMemberAggregate(storeId, end.minusDays(179).toString(), end.toString()));
        result.put("inventoryRisks", analysisMapper.getAiInventoryRisks(storeId, lowStock, 20));
        result.put("tableEfficiency", analysisMapper.getAiTableEfficiency(storeId, start30.toString(), end.toString()));
        result.put("channelPerformance", analysisMapper.getAiChannelPerformance(storeId, start30.toString(), end.toString()));
        result.put("marketingCurrent30Days", analysisMapper.getAiMarketingPerformance(storeId, start30.toString(), end.toString()));
        result.put("marketingPrevious30Days", analysisMapper.getAiMarketingPerformance(storeId, previousStart.toString(), start30.minusDays(1).toString()));
        result.put("orderHealth30Days", analysisMapper.getAiOrderHealth(storeId, start30.toString(), end.toString()));
        result.put("wasteProxy", analysisMapper.getAiWasteProxy(storeId, start30.toString(), end.toString()));
        result.put("dataGaps", List.of("实际盘点损耗尚未接入，当前仅展示退菜代理指标",
                "出餐时长和顾客评价尚无可靠结构化时间戳", "排班工时与天气数据尚未接入"));
        result.put("dataPeriods", "会员近180天、库存实时、经营效率与营销近30天");
        result.put("dataCutoffAt", LocalDateTime.now());
        return result;
    }

    @Override
    public SalesOverviewVO getSalesOverview(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
        SalesOverviewVO overview = new SalesOverviewVO();

        // 计算时间范围：优先使用日期范围参数，否则使用days参数
        LocalDateTime startTime;
        LocalDateTime endTime;
        int actualDays;

        if (startDate != null && endDate != null) {
            // 使用日期范围参数
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
            actualDays = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        } else {
            // 使用days参数：包括今天在内的最近N天
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            actualDays = days;
        }

        overview.setStartTime(startTime.format(DATETIME_FORMATTER));
        overview.setEndTime(endTime.format(DATETIME_FORMATTER));

        // 查询当前周期数据
        Map<String, Object> currentData = analysisMapper.getSalesStatistics(
                storeId, overview.getStartTime(), overview.getEndTime());

        // 查询上期数据（环比）- 前N天
        LocalDateTime lastPeriodEndTime = startTime.minusSeconds(1);
        LocalDateTime lastPeriodStartTime = lastPeriodEndTime.minusDays(actualDays - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        Map<String, Object> lastPeriodData = analysisMapper.getSalesStatistics(
                storeId,
                lastPeriodStartTime.format(DATETIME_FORMATTER),
                lastPeriodEndTime.format(DATETIME_FORMATTER));

        // 查询去年同期数据（同比）
        LocalDateTime lastYearStartTime = startTime.minusYears(1);
        LocalDateTime lastYearEndTime = endTime.minusYears(1);
        Map<String, Object> lastYearData = analysisMapper.getSalesStatistics(
                storeId,
                lastYearStartTime.format(DATETIME_FORMATTER),
                lastYearEndTime.format(DATETIME_FORMATTER));

        // 设置当前周期数据（处理null值）
        overview.setCurrentSales(currentData.get("totalSales") != null ?
                new BigDecimal(currentData.get("totalSales").toString()) : BigDecimal.ZERO);
        overview.setCurrentOrderCount(currentData.get("orderCount") != null ?
                Integer.parseInt(currentData.get("orderCount").toString()) : 0);
        overview.setAvgOrderAmount(currentData.get("avgOrderAmount") != null ?
                new BigDecimal(currentData.get("avgOrderAmount").toString()) : BigDecimal.ZERO);

        // 设置对比数据（处理null值）
        overview.setLastPeriodSales(lastPeriodData.get("totalSales") != null ?
                new BigDecimal(lastPeriodData.get("totalSales").toString()) : BigDecimal.ZERO);
        overview.setLastYearSales(lastYearData.get("totalSales") != null ?
                new BigDecimal(lastYearData.get("totalSales").toString()) : BigDecimal.ZERO);
        overview.setLastPeriodOrderCount(lastPeriodData.get("orderCount") != null ?
                Integer.parseInt(lastPeriodData.get("orderCount").toString()) : 0);

        // 计算增长率
        overview.setPeriodOnPeriodGrowthRate(
                calculateGrowthRate(overview.getCurrentSales(), overview.getLastPeriodSales()));
        overview.setYearOnYearGrowthRate(
                calculateGrowthRate(overview.getCurrentSales(), overview.getLastYearSales()));

        return overview;
    }

    @Override
    public List<DailySalesTrendVO> getSalesTrend(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
        // 计算日期范围：优先使用日期范围参数，否则使用days参数
        LocalDate queryStartDate;
        LocalDate queryEndDate;
        int actualDays;

        if (startDate != null && endDate != null) {
            queryStartDate = startDate;
            queryEndDate = endDate;
            actualDays = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        } else {
            queryEndDate = LocalDate.now();
            queryStartDate = queryEndDate.minusDays(days - 1);
            actualDays = days;
        }

        List<DailySalesTrendVO> trendList = analysisMapper.getDailySalesTrend(
                storeId,
                queryStartDate.format(DATE_FORMATTER),
                queryEndDate.format(DATE_FORMATTER)
        );

        // 填充没有数据的日期（销售额和利润为0）
        List<DailySalesTrendVO> completeList = new ArrayList<>();
        for (int i = 0; i < actualDays; i++) {
            LocalDate date = queryStartDate.plusDays(i);
            String dateStr = date.format(DATE_FORMATTER);

            DailySalesTrendVO trend = trendList.stream()
                    .filter(t -> t.getDate().equals(dateStr))
                    .findFirst()
                    .orElse(new DailySalesTrendVO());

            if (trend.getDate() == null) {
                trend.setDate(dateStr);
                trend.setSales(BigDecimal.ZERO);
                trend.setOrderCount(0);
                trend.setAvgOrderAmount(BigDecimal.ZERO);
            }

            completeList.add(trend);
        }

        return completeList;
    }

    @Override
    public List<TimePeriodSalesVO> getTimePeriodSales(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
        // 计算时间范围：优先使用日期范围参数，否则使用days参数
        LocalDateTime startTime;
        LocalDateTime endTime;
        int actualDays;

        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
            actualDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            actualDays = days;
        }

        List<TimePeriodSalesVO> periodList = analysisMapper.getTimePeriodSales(
                storeId,
                startTime.format(DATETIME_FORMATTER),
                endTime.format(DATETIME_FORMATTER));

        // 计算总销售额和总订单数
        BigDecimal totalSales = periodList.stream()
                .map(TimePeriodSalesVO::getSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalOrders = periodList.stream()
                .mapToInt(TimePeriodSalesVO::getOrderCount)
                .sum();

        // 计算占比
        for (TimePeriodSalesVO period : periodList) {
            period.setAvgDailySales(period.getSales()
                    .divide(new BigDecimal(actualDays), 2, RoundingMode.HALF_UP));
            period.setAvgDailyOrderCount(new BigDecimal(period.getOrderCount())
                    .divide(new BigDecimal(actualDays), 2, RoundingMode.HALF_UP));

            if (totalSales.compareTo(BigDecimal.ZERO) > 0) {
                period.setSalesPercentage(
                        period.getSales()
                                .multiply(new BigDecimal("100"))
                                .divide(totalSales, 2, RoundingMode.HALF_UP)
                );
            } else {
                period.setSalesPercentage(BigDecimal.ZERO);
            }

            if (totalOrders > 0) {
                period.setOrderPercentage(
                        new BigDecimal(period.getOrderCount())
                                .multiply(new BigDecimal("100"))
                                .divide(new BigDecimal(totalOrders), 2, RoundingMode.HALF_UP)
                );
            } else {
                period.setOrderPercentage(BigDecimal.ZERO);
            }
        }

        return periodList;
    }

    @Override
    public OrderAnalysisVO getOrderAnalysis(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
        OrderAnalysisVO analysis = new OrderAnalysisVO();

        // 计算时间范围：优先使用日期范围参数，否则使用days参数
        LocalDateTime startTime;
        LocalDateTime endTime;

        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }

        analysis.setStartTime(startTime.format(DATETIME_FORMATTER));
        analysis.setEndTime(endTime.format(DATETIME_FORMATTER));

        // 查询订单分析数据
        Map<String, Object> data = analysisMapper.getOrderAnalysisData(
                storeId, analysis.getStartTime(), analysis.getEndTime());

        // 设置基础数据（处理null值）
        analysis.setTotalOrderCount(data.get("totalOrderCount") != null ?
                Integer.parseInt(data.get("totalOrderCount").toString()) : 0);
        analysis.setCompletedOrderCount(data.get("completedOrderCount") != null ?
                Integer.parseInt(data.get("completedOrderCount").toString()) : 0);
        analysis.setCancelledOrderCount(data.get("cancelledOrderCount") != null ?
                Integer.parseInt(data.get("cancelledOrderCount").toString()) : 0);
        analysis.setProcessingOrderCount(data.get("processingOrderCount") != null ?
                Integer.parseInt(data.get("processingOrderCount").toString()) : 0);
        analysis.setTotalSales(data.get("totalSales") != null ?
                new BigDecimal(data.get("totalSales").toString()) : BigDecimal.ZERO);
        analysis.setAvgOrderAmount(data.get("avgOrderAmount") != null ?
                new BigDecimal(data.get("avgOrderAmount").toString()) : BigDecimal.ZERO);

        // 计算完成率、取消率、进行率（分母统一为总订单数，三者相加为100%）
        if (analysis.getTotalOrderCount() > 0) {
            BigDecimal total = new BigDecimal(analysis.getTotalOrderCount());

            analysis.setCompletionRate(
                    new BigDecimal(analysis.getCompletedOrderCount())
                            .multiply(new BigDecimal("100"))
                            .divide(total, 2, RoundingMode.HALF_UP)
            );

            analysis.setCancellationRate(
                    new BigDecimal(analysis.getCancelledOrderCount())
                            .multiply(new BigDecimal("100"))
                            .divide(total, 2, RoundingMode.HALF_UP)
            );

            analysis.setProcessingRate(
                    new BigDecimal(analysis.getProcessingOrderCount())
                            .multiply(new BigDecimal("100"))
                            .divide(total, 2, RoundingMode.HALF_UP)
            );
        } else {
            analysis.setCompletionRate(BigDecimal.ZERO);
            analysis.setCancellationRate(BigDecimal.ZERO);
            analysis.setProcessingRate(BigDecimal.ZERO);
        }

        return analysis;
    }

    @Override
    public List<CancelReasonAnalysisVO> getCancelReasonAnalysis(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
        // 计算时间范围：优先使用日期范围参数，否则使用days参数
        LocalDateTime startTime;
        LocalDateTime endTime;

        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }

        List<CancelReasonAnalysisVO> reasonList = analysisMapper.getCancelReasonAnalysis(
                storeId,
                startTime.format(DATETIME_FORMATTER),
                endTime.format(DATETIME_FORMATTER));

        // 计算总取消次数
        int totalCancels = reasonList.stream()
                .mapToInt(CancelReasonAnalysisVO::getCancelCount)
                .sum();

        // 计算占比
        for (CancelReasonAnalysisVO reason : reasonList) {
            if (totalCancels > 0) {
                reason.setPercentage(
                        new BigDecimal(reason.getCancelCount())
                                .multiply(new BigDecimal("100"))
                                .divide(new BigDecimal(totalCancels), 2, RoundingMode.HALF_UP)
                );
            } else {
                reason.setPercentage(BigDecimal.ZERO);
            }
        }

        return reasonList;
    }

    // ==================== 菜品与分类分析 ====================

    @Override
    public List<DishSalesRankingVO> getTopSellingDishes(Long storeId, Integer days, Integer topN, LocalDate startDate, LocalDate endDate) {
        // 计算时间范围：优先使用日期范围参数，否则使用days参数
        LocalDateTime startTime;
        LocalDateTime endTime;

        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }

        List<Map<String, Object>> rawData = analysisMapper.getDishSalesRanking(
                storeId,
                startTime.format(DATETIME_FORMATTER),
                endTime.format(DATETIME_FORMATTER),
                topN);

        Map<String, Object> totalData = analysisMapper.getSalesStatistics(
                storeId,
                startTime.format(DATETIME_FORMATTER),
                endTime.format(DATETIME_FORMATTER));
        int totalOrders = totalData.get("orderCount") == null
                ? 0 : Integer.parseInt(totalData.get("orderCount").toString());

        return buildDishSalesRankingList(rawData, totalOrders);
    }

    @Override
    public List<DishSalesTrendVO> getDishSalesTrend(Long storeId, Long dishId, Integer days,
                                                     LocalDate startDate, LocalDate endDate) {
        LocalDate queryStartDate;
        LocalDate queryEndDate;
        if (startDate != null && endDate != null) {
            queryStartDate = startDate;
            queryEndDate = endDate;
        } else {
            queryEndDate = LocalDate.now();
            queryStartDate = queryEndDate.minusDays(days - 1L);
        }

        List<DishSalesTrendVO> rawData = analysisMapper.getDishSalesTrendByDishId(
                storeId, dishId, queryStartDate.format(DATE_FORMATTER), queryEndDate.format(DATE_FORMATTER));
        Map<LocalDate, DishSalesTrendVO> dataByDate = rawData.stream()
                .collect(Collectors.toMap(DishSalesTrendVO::getDate, item -> item));

        List<DishSalesTrendVO> result = new ArrayList<>();
        long actualDays = ChronoUnit.DAYS.between(queryStartDate, queryEndDate) + 1;
        for (int i = 0; i < actualDays; i++) {
            LocalDate date = queryStartDate.plusDays(i);
            DishSalesTrendVO item = dataByDate.get(date);
            if (item == null) {
                item = new DishSalesTrendVO();
                item.setDate(date);
                item.setSalesQuantity(0);
                item.setSalesAmount(BigDecimal.ZERO);
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public List<DishChangeStatisticsVO> getDishChangeStatistics(Long storeId, Integer days, Integer changeType,
                                                                 LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime;
        LocalDateTime endTime;
        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1L).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        return analysisMapper.getDishChangeStatistics(storeId,
                startTime.format(DATETIME_FORMATTER), endTime.format(DATETIME_FORMATTER), changeType);
    }

    @Override
    public List<SlowMovingDishVO> getSlowMovingDishes(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
        // 计算时间范围：优先使用日期范围参数，否则使用days参数
        LocalDateTime startTime;
        LocalDateTime endTime;
        int actualDays;

        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
            actualDays = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            actualDays = days;
        }

        List<Map<String, Object>> rawData = analysisMapper.getSlowMovingDishes(
                storeId,
                startTime.format(DATETIME_FORMATTER),
                endTime.format(DATETIME_FORMATTER)
        );

        List<SlowMovingDishVO> slowMovingList = new ArrayList<>();
        LocalDateTime referenceTime = endTime.isBefore(LocalDateTime.now()) ? endTime : LocalDateTime.now();

        for (Map<String, Object> data : rawData) {
            SlowMovingDishVO vo = new SlowMovingDishVO();
            vo.setDishId(Long.parseLong(data.get("dishId").toString()));
            vo.setDishName(data.get("dishName").toString());
            vo.setCategoryName(data.get("categoryName") != null ? data.get("categoryName").toString() : "未分类");
            vo.setDishPic(data.get("dishPic") != null ? data.get("dishPic").toString() : "");

            Integer salesLast30Days = data.get("salesLast30Days") != null ?
                    Integer.parseInt(data.get("salesLast30Days").toString()) : 0;
            vo.setSalesQuantity(salesLast30Days);  // 统计期间销量
            vo.setSalesLast30Days(salesLast30Days);

            BigDecimal salesAmount = data.get("salesAmountLast30Days") != null ?
                    new BigDecimal(data.get("salesAmountLast30Days").toString()) : BigDecimal.ZERO;
            vo.setSalesAmount(salesAmount);  // 统计期间销售额
            vo.setSalesAmountLast30Days(salesAmount);

            LocalDateTime createdAtTime = parseDateTime(data.get("createdAt"));
            LocalDateTime activeStartTime = createdAtTime != null && createdAtTime.isAfter(startTime)
                    ? createdAtTime : startTime;
            int activeDays = Math.max(1,
                    (int) ChronoUnit.DAYS.between(activeStartTime.toLocalDate(), endTime.toLocalDate()) + 1);

            // 新上架菜品只按实际上架天数计算，避免被完整统计周期稀释。
            double avgDailySales = salesLast30Days / (double) activeDays;
            vo.setAvgDailySales(Math.round(avgDailySales * 100.0) / 100.0);

            vo.setStock(data.get("stock") != null ? Integer.parseInt(data.get("stock").toString()) : 0);

            if (createdAtTime != null) {
                vo.setCreatedAt(createdAtTime);
            }

            LocalDateTime lastSaleTime = parseDateTime(data.get("lastSaleTime"));
            if (lastSaleTime != null) {
                vo.setLastSaleTime(lastSaleTime);
            }

            LocalDateTime inactivityStart = lastSaleTime != null ? lastSaleTime : createdAtTime;
            int inactiveDays = inactivityStart == null ? 0
                    : Math.max(0, (int) ChronoUnit.DAYS.between(inactivityStart, referenceTime));
            vo.setSlowMovingDays(inactiveDays);

            // 至少观察7天，并同时满足低日均销量和连续未售，才进入行动清单。
            if (activeDays < Math.min(7, actualDays)
                    || avgDailySales >= 0.2
                    || inactiveDays < 7) {
                continue;
            }

            // 生成建议
            vo.setSuggestion(generateSlowMovingSuggestion(vo));

            slowMovingList.add(vo);
        }

        return slowMovingList;
    }

    @Override
    public List<CategoryAnalysisVO> getCategoryAnalysis(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
        // 计算时间范围：优先使用日期范围参数，否则使用days参数
        LocalDateTime startTime;
        LocalDateTime endTime;

        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }

        String startTimeStr = startTime.format(DATETIME_FORMATTER);
        String endTimeStr = endTime.format(DATETIME_FORMATTER);

        List<Map<String, Object>> rawData = analysisMapper.getCategoryContribution(
                storeId, startTimeStr, endTimeStr);

        // 查询总销售额和总销量用于计算占比
        BigDecimal totalSalesAmount = analysisMapper.getTotalSalesAmount(
                storeId, startTimeStr, endTimeStr);
        Integer totalSalesQuantity = analysisMapper.getTotalSalesQuantity(
                storeId, startTimeStr, endTimeStr);

        List<CategoryAnalysisVO> categoryList = new ArrayList<>();

        for (Map<String, Object> data : rawData) {
            CategoryAnalysisVO vo = new CategoryAnalysisVO();
            if (data.get("categoryId") != null) {
                vo.setCategoryId(Long.parseLong(data.get("categoryId").toString()));
            }
            vo.setCategoryName(data.get("categoryName").toString());

            Integer salesQuantity = Integer.parseInt(data.get("salesQuantity").toString());
            vo.setSalesQuantity(salesQuantity);

            BigDecimal salesAmount = new BigDecimal(data.get("salesAmount").toString());
            vo.setSalesAmount(salesAmount);

            // 计算占比
            if (totalSalesAmount.compareTo(BigDecimal.ZERO) > 0) {
                vo.setSalesAmountPercent(salesAmount.multiply(new BigDecimal("100"))
                        .divide(totalSalesAmount, 2, RoundingMode.HALF_UP));
            } else {
                vo.setSalesAmountPercent(BigDecimal.ZERO);
            }

            if (totalSalesQuantity > 0) {
                vo.setSalesQuantityPercent(new BigDecimal(salesQuantity)
                        .multiply(new BigDecimal("100"))
                        .divide(new BigDecimal(totalSalesQuantity), 2, RoundingMode.HALF_UP));
            } else {
                vo.setSalesQuantityPercent(BigDecimal.ZERO);
            }

            vo.setOrderCount(Integer.parseInt(data.get("orderCount").toString()));
            vo.setGiftQuantity(data.get("giftQuantity") == null
                    ? 0 : Integer.parseInt(data.get("giftQuantity").toString()));
            vo.setTotalDiscount(data.get("totalDiscount") == null
                    ? BigDecimal.ZERO : new BigDecimal(data.get("totalDiscount").toString()));

            // 这里表示包含该分类的订单平均消费，不等同于整店客单价。
            if (vo.getOrderCount() > 0) {
                vo.setAvgOrderAmount(salesAmount.divide(
                        new BigDecimal(vo.getOrderCount()), 2, RoundingMode.HALF_UP));
            } else {
                vo.setAvgOrderAmount(BigDecimal.ZERO);
            }

            categoryList.add(vo);
        }

        return categoryList;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算增长率
     */
    private BigDecimal calculateGrowthRate(BigDecimal current, BigDecimal last) {
        if (last.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? null : BigDecimal.ZERO;
        }

        return current.subtract(last)
                .multiply(new BigDecimal("100"))
                .divide(last, 2, RoundingMode.HALF_UP);
    }

    /**
     * 构建菜品销售排行列表
     */
    private List<DishSalesRankingVO> buildDishSalesRankingList(List<Map<String, Object>> rawData,
                                                                int totalOrders) {
        List<DishSalesRankingVO> rankingList = new ArrayList<>();
        int ranking = 1;

        for (Map<String, Object> data : rawData) {
            DishSalesRankingVO vo = new DishSalesRankingVO();
            vo.setRanking(ranking++);
            vo.setDishId(Long.parseLong(data.get("dishId").toString()));
            vo.setDishName(data.get("dishName") == null ? "已删除菜品" : data.get("dishName").toString());

            if (data.get("categoryId") != null) {
                vo.setCategoryId(Long.parseLong(data.get("categoryId").toString()));
            }
            vo.setCategoryName(data.get("categoryName") != null ?
                    data.get("categoryName").toString() : "未分类");
            vo.setDishPic(data.get("dishPic") != null ? data.get("dishPic").toString() : "");

            vo.setSalesQuantity(Integer.parseInt(data.get("salesQuantity").toString()));
            vo.setSalesAmount(new BigDecimal(data.get("salesAmount").toString()));

            int orderCount = data.get("orderCount") == null
                    ? 0 : Integer.parseInt(data.get("orderCount").toString());
            vo.setOrderCount(orderCount);
            vo.setGiftQuantity(data.get("giftQuantity") == null
                    ? 0 : Integer.parseInt(data.get("giftQuantity").toString()));
            vo.setOrderPenetrationRate(totalOrders > 0
                    ? new BigDecimal(orderCount).multiply(new BigDecimal("100"))
                        .divide(new BigDecimal(totalOrders), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            vo.setAvgSellingPrice(vo.getSalesQuantity() > 0
                    ? vo.getSalesAmount().divide(new BigDecimal(vo.getSalesQuantity()), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            // 设置库存
            vo.setStock(data.get("stock") != null ? Integer.parseInt(data.get("stock").toString()) : 0);

            rankingList.add(vo);
        }

        return rankingList;
    }

    /**
     * 生成滞销菜品建议
     */
    private String generateSlowMovingSuggestion(SlowMovingDishVO vo) {
        if (vo.getStock() != null && vo.getStock() > 0 && vo.getSlowMovingDays() >= 30) {
            return "库存积压，建议核查品质、定价或下架";
        }
        if (vo.getLastSaleTime() == null) {
            return "上架后尚无付费销售，建议检查曝光与菜单设置";
        }
        return "近期销售活跃度低，建议检查定价或调整推荐位置";
    }

    /**
     * 兼容MyBatis返回的LocalDateTime和字符串时间。
     */
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        String text = value.toString();
        return text.contains("T")
                ? LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : LocalDateTime.parse(text, DATETIME_FORMATTER);
    }

}
