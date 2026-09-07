package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 销售概览VO
 * 用于展示指定天数内的销售数据及对比
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class SalesOverviewVO {

    /**
     * 当前周期销售额
     */
    private BigDecimal currentSales;

    /**
     * 同比上期销售额（去年同期）
     */
    private BigDecimal lastYearSales;

    /**
     * 环比上期销售额（上个周期）
     */
    private BigDecimal lastPeriodSales;

    /**
     * 同比增长率（%）
     */
    private BigDecimal yearOnYearGrowthRate;

    /**
     * 环比增长率（%）
     */
    private BigDecimal periodOnPeriodGrowthRate;

    /**
     * 当前周期订单数
     */
    private Integer currentOrderCount;

    /**
     * 上期订单数
     */
    private Integer lastPeriodOrderCount;

    /**
     * 平均客单价
     */
    private BigDecimal avgOrderAmount;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
}
