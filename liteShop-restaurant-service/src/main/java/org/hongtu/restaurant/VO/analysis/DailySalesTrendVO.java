package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 每日销售趋势VO
 * 用于展示折线图数据
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class DailySalesTrendVO {

    /**
     * 日期（格式：yyyy-MM-dd）
     */
    private String date;

    /**
     * 当日销售额
     */
    private BigDecimal sales;

    /**
     * 当日订单数
     */
    private Integer orderCount;

    /**
     * 平均客单价
     */
    private BigDecimal avgOrderAmount;

    /**
     * 当日利润
     */
    private BigDecimal profit;
}
