package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.VO.analysis.*;
import org.hongtu.restaurant.service.RestaurantAnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 餐厅数据分析控制器
 * 提供销售分析、订单分析等统计接口
 *
 * @author Ht
 * @since 2025-01-30
 */
@RestController
@RequestMapping("/restaurant-analysis")
public class RestaurantAnalysisController {

    @Resource
    private RestaurantAnalysisService analysisService;

    /**
     * 获取销售概览
     * 包含指定天数内的销售额、同比环比增长率
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 销售概览数据
     *
     * 示例1：GET /restaurant-analysis/sales/overview?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/sales/overview?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/sales/overview")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<SalesOverviewVO> getSalesOverview(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            SalesOverviewVO overview = analysisService.getSalesOverview(storeId, days, startDate, endDate);
            return Result.success("查询销售概览成功", overview);

        
    }

    /**
     * 获取销售趋势图数据
     * 展示近N天的每日净实收、有效订单数和单均消费趋势
     *
     * @param storeId   门店ID（必填）
     * @param days      天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 每日销售趋势列表
     *
     * 示例1：GET /restaurant-analysis/sales/trend?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/sales/trend?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/sales/trend")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<DailySalesTrendVO>> getSalesTrend(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<DailySalesTrendVO> trendList = analysisService.getSalesTrend(storeId, days, startDate, endDate);
            return Result.success("查询销售趋势成功", trendList);

        
    }

    /**
     * 获取时段销售分布
     * 展示早中晚餐时段的销售对比
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 时段销售分布列表
     *
     * 示例1：GET /restaurant-analysis/sales/time-period?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/sales/time-period?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/sales/time-period")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<TimePeriodSalesVO>> getTimePeriodSales(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<TimePeriodSalesVO> periodList = analysisService.getTimePeriodSales(storeId, days, startDate, endDate);
            return Result.success("查询时段销售分布成功", periodList);

        
    }

    /**
     * 获取订单分析数据
     * 包含订单数量统计、平均客单价、订单完成率、订单取消率
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 订单分析数据
     *
     * 示例1：GET /restaurant-analysis/order/analysis?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/order/analysis?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/order/analysis")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<OrderAnalysisVO> getOrderAnalysis(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            OrderAnalysisVO analysis = analysisService.getOrderAnalysis(storeId, days, startDate, endDate);
            return Result.success("查询订单分析成功", analysis);

        
    }

    /**
     * 获取订单状态分布
     * 展示已完成/已取消/进行中的订单占比（饼图）
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 订单状态分布列表
     *
     * 示例1：GET /restaurant-analysis/order/status-distribution?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/order/status-distribution?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @Deprecated
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<OrderStatusDistributionVO>> getOrderStatusDistribution(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<OrderStatusDistributionVO> distributionList = analysisService.getOrderStatusDistribution(
                    storeId, days, startDate, endDate);
            return Result.success("查询订单状态分布成功", distributionList);

        
    }

    /**
     * 获取订单取消原因分析
     * 统计各种取消原因的次数和占比
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 取消原因分析列表
     *
     * 示例1：GET /restaurant-analysis/order/cancel-reason?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/order/cancel-reason?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/order/cancel-reason")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<CancelReasonAnalysisVO>> getCancelReasonAnalysis(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<CancelReasonAnalysisVO> reasonList = analysisService.getCancelReasonAnalysis(
                    storeId, days, startDate, endDate);
            return Result.success("查询取消原因分析成功", reasonList);

        
    }

    // ==================== 菜品与分类分析接口 ====================

    /**
     * 获取热销菜品TOP10
     * 按销量从高到低排序
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param topN      获取前N名，默认10（可选）
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 热销菜品排行列表
     *
     * 示例1：GET /restaurant-analysis/dish/top-selling?storeId=1&days=30&topN=10
     * 示例2：GET /restaurant-analysis/dish/top-selling?storeId=1&startDate=2025-01-01&endDate=2025-01-31&topN=10
     */
    @GetMapping("/dish/top-selling")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<DishSalesRankingVO>> getTopSellingDishes(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "10") Integer topN,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }
            if (topN == null || topN <= 0) {
                return Result.error("topN必须大于0");
            }
            if (topN > 100) {
                return Result.error("topN不能超过100");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<DishSalesRankingVO> topDishes = analysisService.getTopSellingDishes(
                    storeId, days, topN, startDate, endDate);
            return Result.success("查询热销菜品排行成功", topDishes);

        
    }

    /**
     * 获取菜品销售额排行
     * 按销售额从高到低排序
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param topN      获取前N名，默认10（可选）
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 菜品销售额排行列表
     *
     * 示例1：GET /restaurant-analysis/dish/sales-amount-ranking?storeId=1&days=30&topN=10
     * 示例2：GET /restaurant-analysis/dish/sales-amount-ranking?storeId=1&startDate=2025-01-01&endDate=2025-01-31&topN=10
     */
    @Deprecated
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<DishSalesRankingVO>> getDishSalesAmountRanking(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "10") Integer topN,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }
            if (topN == null || topN <= 0) {
                return Result.error("topN必须大于0");
            }
            if (topN > 100) {
                return Result.error("topN不能超过100");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<DishSalesRankingVO> rankingList = analysisService.getDishSalesAmountRanking(
                    storeId, days, topN, startDate, endDate);
            return Result.success("查询菜品销售额排行成功", rankingList);

        
    }

    /**
     * 获取某个菜品的销售趋势
     * 展示该菜品近N天的销量和销售额变化
     *
     * @param storeId   门店ID（必填）
     * @param dishId    菜品ID（必填）
     * @param days      查询天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 菜品销售趋势列表
     *
     * 示例1：GET /restaurant-analysis/dish/sales-trend?storeId=1&dishId=100&days=30
     * 示例2：GET /restaurant-analysis/dish/sales-trend?storeId=1&dishId=100&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/dish/sales-trend")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<DishSalesTrendVO>> getDishSalesTrend(
            @RequestParam Long storeId,
            @RequestParam Long dishId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }
            if (dishId == null) {
                return Result.error("菜品ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<DishSalesTrendVO> trendList = analysisService.getDishSalesTrend(storeId, dishId, days, startDate, endDate);
            return Result.success("查询菜品销售趋势成功", trendList);

        
    }

    /**
     * 获取滞销菜品分析
     * 识别长期销量低的菜品，建议下架或促销
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     *                  注意：滞销分析至少需要7天以上的数据才能准确判断
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 滞销菜品列表
     *
     * 示例1：GET /restaurant-analysis/dish/slow-moving?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/dish/slow-moving?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/dish/slow-moving")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<SlowMovingDishVO>> getSlowMovingDishes(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
                // 检查日期范围是否至少7天
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
                if (daysBetween < 7) {
                    return Result.error("滞销分析至少需要7天以上的数据才能准确判断哟");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days < 7) {
                    return Result.error("滞销分析至少需要7天以上的数据才能准确判断哟");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<SlowMovingDishVO> slowMovingList = analysisService.getSlowMovingDishes(storeId, days, startDate, endDate);
            return Result.success("查询滞销菜品成功", slowMovingList);

        
    }

    /**
     * 获取菜品分类贡献分析
     * 分析各分类的净销售额和付费销量占比
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 分类分析列表
     *
     * 示例1：GET /restaurant-analysis/category/contribution?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/category/contribution?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/category/contribution")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<CategoryAnalysisVO>> getCategoryAnalysis(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<CategoryAnalysisVO> categoryList = analysisService.getCategoryAnalysis(
                    storeId, days, startDate, endDate);
            return Result.success("查询分类贡献分析成功", categoryList);

        
    }

    /**
     * 获取菜品利润率排行
     * 按利润率从高到低排序
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param topN      获取前N名，默认10（可选）
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 菜品利润率排行列表
     *
     * 示例1：GET /restaurant-analysis/dish/profit-rate-ranking?storeId=1&days=30&topN=10
     * 示例2：GET /restaurant-analysis/dish/profit-rate-ranking?storeId=1&startDate=2025-01-01&endDate=2025-01-31&topN=10
     */
    @Deprecated
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<DishSalesRankingVO>> getDishProfitRateRanking(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "10") Integer topN,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }
            if (topN == null || topN <= 0) {
                return Result.error("topN必须大于0");
            }
            if (topN > 100) {
                return Result.error("topN不能超过100");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<DishSalesRankingVO> profitRateList = analysisService.getDishProfitRateRanking(
                    storeId, days, topN, startDate, endDate);
            return Result.success("查询菜品利润率排行成功", profitRateList);

        
    }

    /**
     * 获取菜品利润矩阵分析
     * 将菜品置于"销量-利润率"四象限矩阵中：
     * - 金牛产品（高销量、高利润）：核心优势，全力保障
     * - 明星产品（低销量、高利润）：可尝试推广、推荐
     * - 引流产品（高销量、低利润）：控制成本，或小幅提价
     * - 瘦狗产品（低销量、低利润）：首要下架候选
     *
     * @param storeId   门店ID（必填）
     * @param days      统计天数，默认30天（可选）。表示包括今天在内的最近N天，例如：days=1查询当天，days=7查询今天+过去6天
     * @param startDate 开始日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @param endDate   结束日期（可选，格式：yyyy-MM-dd）。如果提供了startDate和endDate，则忽略days参数
     * @return 菜品利润矩阵列表
     *
     * 示例1：GET /restaurant-analysis/dish/profit-matrix?storeId=1&days=30
     * 示例2：GET /restaurant-analysis/dish/profit-matrix?storeId=1&startDate=2025-01-01&endDate=2025-01-31
     */
    @Deprecated
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<DishProfitMatrixVO>> getDishProfitMatrix(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }

            // 日期范围参数校验
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else {
                // 使用days参数时的校验
                if (days == null || days <= 0) {
                    return Result.error("天数必须大于0");
                }
                if (days > 365) {
                    return Result.error("天数不能超过365天");
                }
            }

            List<DishProfitMatrixVO> matrixList = analysisService.getDishProfitMatrix(
                    storeId, days, startDate, endDate);
            return Result.success("查询菜品利润矩阵成功", matrixList);

        
    }

    /**
     * 获取年度月度销售额和利润数据
     * 展示指定年份每个月的销售额、利润和利润率
     *
     * @param storeId 门店ID（必填）
     * @param year    年份（必填，例如：2025）
     * @return 每月销售额和利润列表
     *
     * 示例：GET /restaurant-analysis/sales/monthly-profit?storeId=1&year=2025
     */
    @Deprecated
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<MonthlySalesProfitVO>> getMonthlySalesProfit(
            @RequestParam Long storeId,
            @RequestParam Integer year) {
        
            // 参数校验
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }
            if (year == null) {
                return Result.error("年份不能为空");
            }
            if (year < 2000 || year > 2100) {
                return Result.error("年份必须在2000-2100之间");
            }
            if (year > LocalDate.now().getYear()) {
                return Result.error("年份不能晚于当前年份");
            }

            List<MonthlySalesProfitVO> monthlyList = analysisService.getMonthlySalesProfit(storeId, year);
            return Result.success("查询年度月度销售和利润成功", monthlyList);

        
    }

    /**
     * 获取加退菜统计。退菜数据以不可变事件日志为准。
     */
    @GetMapping("/dish/change-statistics")
    @SaCheckPermission(value = {"restaurant:analysis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<DishChangeStatisticsVO>> getDishChangeStatistics(
            @RequestParam Long storeId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false, defaultValue = "1") Integer changeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
            if (storeId == null) {
                return Result.error("门店ID不能为空");
            }
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    return Result.error("开始日期不能晚于结束日期");
                }
                if (endDate.isAfter(LocalDate.now())) {
                    return Result.error("结束日期不能晚于当前日期");
                }
            } else if (startDate != null || endDate != null) {
                return Result.error("开始日期和结束日期必须同时提供");
            } else if (days == null || days <= 0 || days > 365) {
                return Result.error("天数必须在1-365之间");
            }
            if (changeType != 1 && changeType != 2) {
                return Result.error("统计类型只能是1-加菜或2-退菜");
            }
            return Result.success("查询加退菜统计成功",
                    analysisService.getDishChangeStatistics(storeId, days, changeType, startDate, endDate));
        
    }

}
