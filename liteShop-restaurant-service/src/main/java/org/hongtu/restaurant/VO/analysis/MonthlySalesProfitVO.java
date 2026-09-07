package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 每月销售额和利润VO
 * 用于展示年度月度销售趋势
 *
 * @author Ht
 * @since 2025-01-31
 */
@Data
public class MonthlySalesProfitVO {

    /**
     * 年份
     */
    private Integer year;

    /**
     * 月份（1-12）
     */
    private Integer month;

    /**
     * 月份名称（格式：yyyy-MM）
     */
    private String monthName;

    /**
     * 当月销售额
     */
    private BigDecimal sales;

    /**
     * 当月利润
     */
    private BigDecimal profit;

    /**
     * 当月订单数
     */
    private Integer orderCount;

    /**
     * 利润率（百分比）
     */
    private BigDecimal profitRate;
}
