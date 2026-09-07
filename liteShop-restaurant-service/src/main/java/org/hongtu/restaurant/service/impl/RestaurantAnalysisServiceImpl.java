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
                trend.setProfit(BigDecimal.ZERO);
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

        if (startDate != null && endDate != null) {
            startTime = startDate.atStartOfDay();
            endTime = endDate.atTime(23, 59, 59);
        } else {
            endTime = LocalDateTime.now();
            startTime = endTime.minusDays(days - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
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
    public List<OrderStatusDistributionVO> getOrderStatusDistribution(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
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

        List<OrderStatusDistributionVO> distributionList = analysisMapper.getOrderStatusDistribution(
                storeId,
                startTime.format(DATETIME_FORMATTER),
                endTime.format(DATETIME_FORMATTER));

        // 计算总订单数
        int totalOrders = distributionList.stream()
                .mapToInt(OrderStatusDistributionVO::getOrderCount)
                .sum();

        // 计算占比
        for (OrderStatusDistributionVO distribution : distributionList) {
            if (totalOrders > 0) {
                distribution.setPercentage(
                        new BigDecimal(distribution.getOrderCount())
                                .multiply(new BigDecimal("100"))
                                .divide(new BigDecimal(totalOrders), 2, RoundingMode.HALF_UP)
                );
            } else {
                distribution.setPercentage(BigDecimal.ZERO);
            }
        }

        return distributionList;
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

        return buildDishSalesRankingList(rawData);
    }

    @Override
    public List<DishSalesRankingVO> getDishSalesAmountRanking(Long storeId, Integer days, Integer topN, LocalDate startDate, LocalDate endDate) {
        // 与热销菜品类似，但在SQL中按销售额排序
        return getTopSellingDishes(storeId, days, topN, startDate, endDate);
    }

    @Override
    public List<DishSalesTrendVO> getDishSalesTrend(Long storeId, Long dishId, Integer days, LocalDate startDate, LocalDate endDate) {
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

        List<Map<String, Object>> rawData = analysisMapper.getDishSalesTrendByDishId(
                storeId,
                dishId,
                queryStartDate.format(DATE_FORMATTER),
                queryEndDate.format(DATE_FORMATTER)
        );

        // 填充完整日期数据
        List<DishSalesTrendVO> trendList = new ArrayList<>();
        for (int i = 0; i < actualDays; i++) {
            LocalDate date = queryStartDate.plusDays(i);
            final LocalDate finalDate = date;

            // 查找当天是否有数据
            Map<String, Object> dayData = rawData.stream()
                    .filter(data -> {
                        String dataDate = data.get("date").toString();
                        return dataDate.equals(finalDate.format(DATE_FORMATTER));
                    })
                    .findFirst()
                    .orElse(null);

            DishSalesTrendVO trend = new DishSalesTrendVO();
            trend.setDate(date);

            if (dayData != null) {
                trend.setSalesQuantity(Integer.parseInt(dayData.get("salesQuantity").toString()));
                trend.setSalesAmount(new BigDecimal(dayData.get("salesAmount").toString()));
            } else {
                trend.setSalesQuantity(0);
                trend.setSalesAmount(BigDecimal.ZERO);
            }

            trendList.add(trend);
        }

        return trendList;
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

            // 计算平均日销量
            double avgDailySales = salesLast30Days / (double) actualDays;
            vo.setAvgDailySales(Math.round(avgDailySales * 100.0) / 100.0);

            vo.setStock(data.get("stock") != null ? Integer.parseInt(data.get("stock").toString()) : 0);

            if (data.get("createdAt") != null) {
                // 处理数据库返回的时间字段，可能是LocalDateTime对象或ISO格式字符串
                Object createdAtObj = data.get("createdAt");
                LocalDateTime createdAtTime;

                if (createdAtObj instanceof LocalDateTime) {
                    createdAtTime = (LocalDateTime) createdAtObj;
                } else {
                    String createdAtStr = createdAtObj.toString();
                    // 尝试使用ISO格式解析（格式：2026-01-28T21:06:47）
                    if (createdAtStr.contains("T")) {
                        createdAtTime = LocalDateTime.parse(createdAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } else {
                        // 使用标准格式解析（格式：2026-01-28 21:06:47）
                        createdAtTime = LocalDateTime.parse(createdAtStr, DATETIME_FORMATTER);
                    }
                }

                vo.setCreatedAt(createdAtTime);
                long slowMovingDays = ChronoUnit.DAYS.between(createdAtTime, LocalDateTime.now());
                vo.setSlowMovingDays((int) slowMovingDays);
            }

            // 处理最后一次销售时间
            if (data.get("lastSaleTime") != null) {
                Object lastSaleTimeObj = data.get("lastSaleTime");
                LocalDateTime lastSaleTime;

                if (lastSaleTimeObj instanceof LocalDateTime) {
                    lastSaleTime = (LocalDateTime) lastSaleTimeObj;
                } else {
                    String lastSaleTimeStr = lastSaleTimeObj.toString();
                    // 尝试使用ISO格式解析（格式：2026-01-28T21:06:47）
                    if (lastSaleTimeStr.contains("T")) {
                        lastSaleTime = LocalDateTime.parse(lastSaleTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } else {
                        // 使用标准格式解析（格式：2026-01-28 21:06:47）
                        lastSaleTime = LocalDateTime.parse(lastSaleTimeStr, DATETIME_FORMATTER);
                    }
                }

                vo.setLastSaleTime(lastSaleTime);
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
            vo.setCategoryId(Long.parseLong(data.get("categoryId").toString()));
            vo.setCategoryName(data.get("categoryName").toString());

            Integer salesQuantity = Integer.parseInt(data.get("salesQuantity").toString());
            vo.setSalesQuantity(salesQuantity);

            BigDecimal salesAmount = new BigDecimal(data.get("salesAmount").toString());
            vo.setSalesAmount(salesAmount);

            BigDecimal totalCost = data.get("totalCost") != null ?
                    new BigDecimal(data.get("totalCost").toString()) : BigDecimal.ZERO;
            vo.setTotalCost(totalCost);

            BigDecimal totalProfit = salesAmount.subtract(totalCost);
            vo.setTotalProfit(totalProfit);

            // 计算利润率
            if (salesAmount.compareTo(BigDecimal.ZERO) > 0) {
                vo.setProfitRate(totalProfit.multiply(new BigDecimal("100"))
                        .divide(salesAmount, 2, RoundingMode.HALF_UP));
            } else {
                vo.setProfitRate(BigDecimal.ZERO);
            }

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

            // 计算平均客单价
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

    @Override
    public List<DishSalesRankingVO> getDishProfitRateRanking(Long storeId, Integer days, Integer topN, LocalDate startDate, LocalDate endDate) {
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
                999); // 先查询所有数据

        List<DishSalesRankingVO> rankingList = buildDishSalesRankingList(rawData);

        // 按利润率排序并取前N名
        return rankingList.stream()
                .sorted((a, b) -> b.getProfitRate().compareTo(a.getProfitRate()))
                .limit(topN)
                .peek(vo -> vo.setRanking(rankingList.indexOf(vo) + 1))
                .toList();
    }

    @Override
    public List<DishProfitMatrixVO> getDishProfitMatrix(Long storeId, Integer days, LocalDate startDate, LocalDate endDate) {
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
                999);

        if (rawData.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算销量和利润率的中位数
        List<Integer> salesQuantities = rawData.stream()
                .map(data -> Integer.parseInt(data.get("salesQuantity").toString()))
                .sorted()
                .toList();

        List<BigDecimal> profitRates = rawData.stream()
                .map(data -> {
                    BigDecimal salesAmount = new BigDecimal(data.get("salesAmount").toString());
                    BigDecimal cost = data.get("totalCost") != null ?
                            new BigDecimal(data.get("totalCost").toString()) : BigDecimal.ZERO;
                    BigDecimal profit = salesAmount.subtract(cost);

                    if (salesAmount.compareTo(BigDecimal.ZERO) > 0) {
                        return profit.multiply(new BigDecimal("100"))
                                .divide(salesAmount, 2, RoundingMode.HALF_UP);
                    }
                    return BigDecimal.ZERO;
                })
                .sorted()
                .toList();

        // 计算中位数
        int medianIndex = salesQuantities.size() / 2;
        int medianSalesQuantity = salesQuantities.get(medianIndex);
        BigDecimal medianProfitRate = profitRates.get(medianIndex);

        // 构建矩阵数据
        List<DishProfitMatrixVO> matrixList = new ArrayList<>();

        for (Map<String, Object> data : rawData) {
            DishProfitMatrixVO vo = new DishProfitMatrixVO();
            vo.setDishId(Long.parseLong(data.get("dishId").toString()));
            vo.setDishName(data.get("dishName").toString());
            vo.setCategoryName(data.get("categoryName") != null ?
                    data.get("categoryName").toString() : "未分类");
            vo.setDishPic(data.get("dishPic") != null ? data.get("dishPic").toString() : "");

            Integer salesQuantity = Integer.parseInt(data.get("salesQuantity").toString());
            vo.setSalesQuantity(salesQuantity);

            BigDecimal salesAmount = new BigDecimal(data.get("salesAmount").toString());
            vo.setSalesAmount(salesAmount);

            BigDecimal cost = data.get("totalCost") != null ?
                    new BigDecimal(data.get("totalCost").toString()) : BigDecimal.ZERO;
            BigDecimal profit = salesAmount.subtract(cost);
            vo.setProfit(profit);

            // 计算利润率
            BigDecimal profitRate;
            if (salesAmount.compareTo(BigDecimal.ZERO) > 0) {
                profitRate = profit.multiply(new BigDecimal("100"))
                        .divide(salesAmount, 2, RoundingMode.HALF_UP);
            } else {
                profitRate = BigDecimal.ZERO;
            }
            vo.setProfitRate(profitRate);

            // 判断象限
            boolean isHighSales = salesQuantity >= medianSalesQuantity;
            boolean isHighProfit = profitRate.compareTo(medianProfitRate) >= 0;

            if (isHighSales && isHighProfit) {
                vo.setQuadrantType("GOLDEN_COW");
                vo.setQuadrantDesc("金牛产品（高销量、高利润）");
                vo.setStrategySuggestion("核心优势产品，确保供应充足，保持品质稳定，可作为主推产品");
            } else if (!isHighSales && isHighProfit) {
                vo.setQuadrantType("STAR");
                vo.setQuadrantDesc("明星产品（低销量、高利润）");
                vo.setStrategySuggestion("潜力产品，建议加大推广力度，通过套餐、推荐等方式提升销量");
            } else if (isHighSales && !isHighProfit) {
                vo.setQuadrantType("CASH_COW");
                vo.setQuadrantDesc("引流产品（高销量、低利润）");
                vo.setStrategySuggestion("流量产品，控制成本，可考虑小幅提价，或作为引流产品配合高利润产品销售");
            } else {
                vo.setQuadrantType("DOG");
                vo.setQuadrantDesc("瘦狗产品（低销量、低利润）");
                vo.setStrategySuggestion("优先下架候选，建议评估是否继续销售，或进行促销清仓");
            }

            matrixList.add(vo);
        }

        return matrixList;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算增长率
     */
    private BigDecimal calculateGrowthRate(BigDecimal current, BigDecimal last) {
        if (last.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("100") : BigDecimal.ZERO;
        }

        return current.subtract(last)
                .multiply(new BigDecimal("100"))
                .divide(last, 2, RoundingMode.HALF_UP);
    }

    /**
     * 构建菜品销售排行列表
     */
    private List<DishSalesRankingVO> buildDishSalesRankingList(List<Map<String, Object>> rawData) {
        List<DishSalesRankingVO> rankingList = new ArrayList<>();
        int ranking = 1;

        for (Map<String, Object> data : rawData) {
            DishSalesRankingVO vo = new DishSalesRankingVO();
            vo.setRanking(ranking++);
            vo.setDishId(Long.parseLong(data.get("dishId").toString()));
            vo.setDishName(data.get("dishName").toString());

            if (data.get("categoryId") != null) {
                vo.setCategoryId(Long.parseLong(data.get("categoryId").toString()));
            }
            vo.setCategoryName(data.get("categoryName") != null ?
                    data.get("categoryName").toString() : "未分类");
            vo.setDishPic(data.get("dishPic") != null ? data.get("dishPic").toString() : "");

            vo.setSalesQuantity(Integer.parseInt(data.get("salesQuantity").toString()));
            vo.setSalesAmount(new BigDecimal(data.get("salesAmount").toString()));

            // 成本价和利润
            BigDecimal costPrice = data.get("costPrice") != null ?
                    new BigDecimal(data.get("costPrice").toString()) : BigDecimal.ZERO;
            vo.setCostPrice(costPrice);

            BigDecimal totalCost = data.get("totalCost") != null ?
                    new BigDecimal(data.get("totalCost").toString()) : BigDecimal.ZERO;
            BigDecimal profit = vo.getSalesAmount().subtract(totalCost);
            vo.setProfit(profit);

            // 计算利润率
            if (vo.getSalesAmount().compareTo(BigDecimal.ZERO) > 0) {
                vo.setProfitRate(profit.multiply(new BigDecimal("100"))
                        .divide(vo.getSalesAmount(), 2, RoundingMode.HALF_UP));
            } else {
                vo.setProfitRate(BigDecimal.ZERO);
            }

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
        if (vo.getAvgDailySales() == 0) {
            return "严重滞销，建议立即下架";
        } else if (vo.getAvgDailySales() < 1) {
            return "销量极低，建议考虑促销或下架";
        } else if (vo.getAvgDailySales() < 3) {
            return "销量较低，建议进行促销活动或调整定价";
        } else {
            return "销量一般，可尝试优化菜品或营销推广";
        }
    }

    @Override
    public List<MonthlySalesProfitVO> getMonthlySalesProfit(Long storeId, Integer year) {
        // 查询年度月度数据
        List<MonthlySalesProfitVO> monthlyList = analysisMapper.getMonthlySalesProfit(storeId, year);

        // 填充没有数据的月份（销售额和利润为0）
        List<MonthlySalesProfitVO> completeList = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int finalMonth = month;
            MonthlySalesProfitVO vo = monthlyList.stream()
                    .filter(m -> m.getMonth() != null && m.getMonth() == finalMonth)
                    .findFirst()
                    .orElse(new MonthlySalesProfitVO());

            if (vo.getMonth() == null) {
                vo.setYear(year);
                vo.setMonth(month);
                vo.setMonthName(String.format("%d-%02d", year, month));
                vo.setSales(BigDecimal.ZERO);
                vo.setProfit(BigDecimal.ZERO);
                vo.setOrderCount(0);
                vo.setProfitRate(BigDecimal.ZERO);
            }

            completeList.add(vo);
        }

        return completeList;
    }
}
