package org.hongtu.restaurant.service;

import org.hongtu.restaurant.VO.analysis.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 餐厅数据分析服务接口
 *
 * @author Ht
 * @since 2025-01-30
 */
public interface RestaurantAnalysisService {

    /**
     * 获取销售概览（包含同比环比）
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 销售概览数据
     */
    SalesOverviewVO getSalesOverview(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取销售趋势（近N天）
     *
     * @param storeId   门店ID
     * @param days      天数（如30天）
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 每日销售趋势列表
     */
    List<DailySalesTrendVO> getSalesTrend(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取时段销售分布
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 时段销售分布列表
     */
    List<TimePeriodSalesVO> getTimePeriodSales(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取订单分析数据
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 订单分析数据
     */
    OrderAnalysisVO getOrderAnalysis(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取订单状态分布
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 订单状态分布列表
     */
    List<OrderStatusDistributionVO> getOrderStatusDistribution(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取订单取消原因分析
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 取消原因分析列表
     */
    List<CancelReasonAnalysisVO> getCancelReasonAnalysis(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    // ==================== 菜品与分类分析 ====================

    /**
     * 获取热销菜品TOP10（按销量排行）
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param topN      获取前N名，默认10
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 热销菜品排行列表
     */
    List<DishSalesRankingVO> getTopSellingDishes(Long storeId, Integer days, Integer topN, LocalDate startDate, LocalDate endDate);

    /**
     * 获取菜品销售额排行
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param topN      获取前N名，默认10
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 菜品销售额排行列表
     */
    List<DishSalesRankingVO> getDishSalesAmountRanking(Long storeId, Integer days, Integer topN, LocalDate startDate, LocalDate endDate);

    /**
     * 获取某个菜品的销售趋势
     *
     * @param storeId   门店ID
     * @param dishId    菜品ID
     * @param days      查询天数，默认30天
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 菜品销售趋势列表
     */
    List<DishSalesTrendVO> getDishSalesTrend(Long storeId, Long dishId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取滞销菜品分析
     *
     * @param storeId   门店ID
     * @param days      统计天数，默认30天
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 滞销菜品列表
     */
    List<SlowMovingDishVO> getSlowMovingDishes(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取菜品分类贡献分析
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 分类分析列表
     */
    List<CategoryAnalysisVO> getCategoryAnalysis(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取菜品利润率排行
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param topN      获取前N名，默认10
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 菜品利润率排行列表
     */
    List<DishSalesRankingVO> getDishProfitRateRanking(Long storeId, Integer days, Integer topN, LocalDate startDate, LocalDate endDate);

    /**
     * 获取菜品利润矩阵分析（销量-利润率四象限）
     *
     * @param storeId   门店ID
     * @param days      统计天数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 菜品利润矩阵列表
     */
    List<DishProfitMatrixVO> getDishProfitMatrix(Long storeId, Integer days, LocalDate startDate, LocalDate endDate);

    /**
     * 获取年度月度销售额和利润数据
     *
     * @param storeId 门店ID
     * @param year    年份
     * @return 每月销售额和利润列表
     */
    List<MonthlySalesProfitVO> getMonthlySalesProfit(Long storeId, Integer year);
}
