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

    /** 查询指定菜品每日净销售趋势。 */
    List<DishSalesTrendVO> getDishSalesTrendByDishId(@Param("storeId") Long storeId,
                                                      @Param("dishId") Long dishId,
                                                      @Param("startDate") String startDate,
                                                      @Param("endDate") String endDate);

    /** 查询加退菜统计。 */
    List<DishChangeStatisticsVO> getDishChangeStatistics(@Param("storeId") Long storeId,
                                                          @Param("startTime") String startTime,
                                                          @Param("endTime") String endTime,
                                                          @Param("changeType") Integer changeType);

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

    /** 匿名会员经营分群，只返回人数和订单聚合，不返回用户标识。 */
    Map<String, Object> getAiMemberAggregate(@Param("storeId") Long storeId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    /** 最近30天堂食、自提等渠道经营表现。 */
    List<Map<String, Object>> getAiChannelPerformance(@Param("storeId") Long storeId,
                                                       @Param("startDate") String startDate,
                                                       @Param("endDate") String endDate);

    /** 库存跟踪菜品的低库存和售罄风险，仅返回有限样本。 */
    List<Map<String, Object>> getAiInventoryRisks(@Param("storeId") Long storeId,
                                                   @Param("lowStock") Integer lowStock,
                                                   @Param("limit") Integer limit);

    /** 桌台翻台和平均就餐时长聚合。 */
    Map<String, Object> getAiTableEfficiency(@Param("storeId") Long storeId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    /** 优惠与优惠券订单的经营成本代理指标。 */
    Map<String, Object> getAiMarketingPerformance(@Param("storeId") Long storeId,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);

    /** 订单取消率等经营健康指标。 */
    Map<String, Object> getAiOrderHealth(@Param("storeId") Long storeId,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate);

    /** 退菜数量和金额作为损耗代理指标，并明确不等同于实际盘点损耗。 */
    Map<String, Object> getAiWasteProxy(@Param("storeId") Long storeId,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate);

}
