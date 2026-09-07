package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 菜品分类贡献分析VO
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class CategoryAnalysisVO {

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 销售数量
     */
    private Integer salesQuantity;

    /**
     * 销售额
     */
    private BigDecimal salesAmount;

    /**
     * 销售额占比（百分比）
     */
    private BigDecimal salesAmountPercent;

    /**
     * 销量占比（百分比）
     */
    private BigDecimal salesQuantityPercent;

    /**
     * 总成本
     */
    private BigDecimal totalCost;

    /**
     * 总利润
     */
    private BigDecimal totalProfit;

    /**
     * 利润率（百分比）
     */
    private BigDecimal profitRate;

    /**
     * 订单数量
     */
    private Integer orderCount;

    /**
     * 平均客单价
     */
    private BigDecimal avgOrderAmount;
}
