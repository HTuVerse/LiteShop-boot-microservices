package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.hongtu.restaurant.VO.analysis.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 餐厅数据分析Mapper
 *
 * @author Ht
 * @since 2025-01-30
 */
@Mapper
public interface RestaurantAnalysisMapper {

    /**
     * 查询指定时间范围内的销售额和订单数
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 销售统计数据
     */
    Map<String, Object> getSalesStatistics(@Param("storeId") Long storeId,
                                           @Param("startTime") String startTime,
                                           @Param("endTime") String endTime);

    /**
     * 查询每日销售趋势（近N天）
     *
     * @param storeId   门店ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 每日销售趋势列表
     */
    List<DailySalesTrendVO> getDailySalesTrend(@Param("storeId") Long storeId,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    /**
     * 查询时段销售分布
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 时段销售分布列表
     */
    List<TimePeriodSalesVO> getTimePeriodSales(@Param("storeId") Long storeId,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime);

    /**
     * 查询订单状态分布
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 订单状态分布列表
     */
    List<OrderStatusDistributionVO> getOrderStatusDistribution(@Param("storeId") Long storeId,
                                                               @Param("startTime") String startTime,
                                                               @Param("endTime") String endTime);

    /**
     * 查询订单取消原因分析
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 取消原因分析列表
     */
    List<CancelReasonAnalysisVO> getCancelReasonAnalysis(@Param("storeId") Long storeId,
                                                         @Param("startTime") String startTime,
                                                         @Param("endTime") String endTime);

    /**
     * 查询订单分析数据
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 订单分析数据
     */
    Map<String, Object> getOrderAnalysisData(@Param("storeId") Long storeId,
                                             @Param("startTime") String startTime,
                                             @Param("endTime") String endTime);

    // ==================== 菜品与分类分析 ====================

    /**
     * 查询菜品销售排行数据
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     限制数量
     * @return 菜品销售排行列表
     */
    List<Map<String, Object>> getDishSalesRanking(@Param("storeId") Long storeId,
                                                  @Param("startTime") String startTime,
                                                  @Param("endTime") String endTime,
                                                  @Param("limit") Integer limit);

    /**
     * 查询某个菜品的销售趋势
     *
     * @param storeId   门店ID
     * @param dishId    菜品ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 菜品销售趋势列表
     */
    List<Map<String, Object>> getDishSalesTrendByDishId(@Param("storeId") Long storeId,
                                                        @Param("dishId") Long dishId,
                                                        @Param("startDate") String startDate,
                                                        @Param("endDate") String endDate);

    /**
     * 查询滞销菜品数据
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 滞销菜品列表
     */
    List<Map<String, Object>> getSlowMovingDishes(@Param("storeId") Long storeId,
                                                  @Param("startTime") String startTime,
                                                  @Param("endTime") String endTime);

    /**
     * 查询菜品分类贡献分析数据
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 分类分析列表
     */
    List<Map<String, Object>> getCategoryContribution(@Param("storeId") Long storeId,
                                                      @Param("startTime") String startTime,
                                                      @Param("endTime") String endTime);

    /**
     * 查询总销售额（用于计算占比）
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 总销售额
     */
    BigDecimal getTotalSalesAmount(@Param("storeId") Long storeId,
                                   @Param("startTime") String startTime,
                                   @Param("endTime") String endTime);

    /**
     * 查询总销量（用于计算占比）
     *
     * @param storeId   门店ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 总销量
     */
    Integer getTotalSalesQuantity(@Param("storeId") Long storeId,
                                  @Param("startTime") String startTime,
                                  @Param("endTime") String endTime);

    /**
     * 查询年度月度销售额和利润数据
     *
     * @param storeId 门店ID
     * @param year    年份
     * @return 每月销售额和利润列表
     */
    List<MonthlySalesProfitVO> getMonthlySalesProfit(@Param("storeId") Long storeId,
                                                     @Param("year") Integer year);
}
