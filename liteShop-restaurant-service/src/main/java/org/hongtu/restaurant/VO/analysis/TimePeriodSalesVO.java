package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 时段销售分布VO
 * 用于展示早中晚餐时段的销售对比
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class TimePeriodSalesVO {

    /**
     * 时段名称（早餐/午餐/晚餐/夜宵）
     */
    private String periodName;

    /**
     * 时段范围（如：06:00-10:00）
     */
    private String timeRange;

    /**
     * 销售额
     */
    private BigDecimal sales;

    /**
     * 订单数
     */
    private Integer orderCount;

    /**
     * 销售额占比（%）
     */
    private BigDecimal salesPercentage;

    /**
     * 订单数占比（%）
     */
    private BigDecimal orderPercentage;
}
