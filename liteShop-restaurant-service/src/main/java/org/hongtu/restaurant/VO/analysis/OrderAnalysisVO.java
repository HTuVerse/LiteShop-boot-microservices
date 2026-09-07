package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单分析VO
 * 用于展示订单相关的统计数据
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class OrderAnalysisVO {

    /**
     * 订单总数
     */
    private Integer totalOrderCount;

    /**
     * 已完成订单数
     */
    private Integer completedOrderCount;

    /**
     * 已取消订单数
     */
    private Integer cancelledOrderCount;

    /**
     * 进行中订单数
     */
    private Integer processingOrderCount;

    /**
     * 订单完成率（%）
     */
    private BigDecimal completionRate;

    /**
     * 订单取消率（%）
     */
    private BigDecimal cancellationRate;

    /**
     * 订单进行率（%）
     */
    private BigDecimal processingRate;

    /**
     * 平均客单价
     */
    private BigDecimal avgOrderAmount;

    /**
     * 总销售额
     */
    private BigDecimal totalSales;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
}
