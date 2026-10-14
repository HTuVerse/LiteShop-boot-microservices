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
     * 订单数量
     */
    private Integer orderCount;

    /** 赠送菜品数量。 */
    private Integer giftQuantity;

    /** 分类订单项优惠金额。 */
    private BigDecimal totalDiscount;

    /**
     * 包含该分类的订单平均消费
     */
    private BigDecimal avgOrderAmount;
}
