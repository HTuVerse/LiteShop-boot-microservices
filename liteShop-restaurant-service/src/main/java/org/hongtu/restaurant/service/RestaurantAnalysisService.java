package org.hongtu.restaurant.service;

import org.hongtu.restaurant.VO.analysis.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    /** 获取指定菜品每日净销售趋势。 */
    List<DishSalesTrendVO> getDishSalesTrend(Long storeId, Long dishId, Integer days,
                                              LocalDate startDate, LocalDate endDate);

    /** 获取加退菜统计。 */
    List<DishChangeStatisticsVO> getDishChangeStatistics(Long storeId, Integer days, Integer changeType,
                                                          LocalDate startDate, LocalDate endDate);

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

    /** 已下线：历史成本没有成交快照，不能继续输出利润类指标。 */
    @Deprecated
    default List<MonthlySalesProfitVO> getMonthlySalesProfit(Long storeId, Integer year) {
        throw new UnsupportedOperationException("月度利润指标已下线");
    }

    /** 已下线：历史状态分布属于实时履约监控。 */
    @Deprecated
    default List<OrderStatusDistributionVO> getOrderStatusDistribution(Long storeId, Integer days,
                                                                         LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException("历史订单状态分布已下线");
    }

    /** 已下线：销售额排行已合并到菜品经营表现。 */
    @Deprecated
    default List<DishSalesRankingVO> getDishSalesAmountRanking(Long storeId, Integer days, Integer topN,
                                                                LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException("重复菜品销售额排行已下线");
    }

    /** 已下线：无成交成本快照时利润率不可作为决策依据。 */
    @Deprecated
    default List<DishSalesRankingVO> getDishProfitRateRanking(Long storeId, Integer days, Integer topN,
                                                               LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException("菜品利润率排行已下线");
    }

    /** 已下线：中位数四象限不能可靠指导菜单决策。 */
    @Deprecated
    default List<DishProfitMatrixVO> getDishProfitMatrix(Long storeId, Integer days,
                                                          LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException("菜品利润矩阵已下线");
    }

    /** AI 经营分析使用的有界匿名聚合快照。 */
    Map<String, Object> getAiExtendedAnalysisSnapshot(Long storeId);
}
