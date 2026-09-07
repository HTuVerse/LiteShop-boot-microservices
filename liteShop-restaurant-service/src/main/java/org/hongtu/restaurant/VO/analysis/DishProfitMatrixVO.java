package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 菜品利润矩阵VO（销量-利润率四象限分析）
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class DishProfitMatrixVO {

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 菜品名称
     */
    private String dishName;

    /**
     * 菜品分类名称
     */
    private String categoryName;

    /**
     * 菜品图片
     */
    private String dishPic;

    /**
     * 销售数量
     */
    private Integer salesQuantity;

    /**
     * 销售额
     */
    private BigDecimal salesAmount;

    /**
     * 利润
     */
    private BigDecimal profit;

    /**
     * 利润率（百分比）
     */
    private BigDecimal profitRate;

    /**
     * 象限类型：
     * GOLDEN_COW - 金牛产品（高销量、高利润）
     * STAR - 明星产品（低销量、高利润）
     * CASH_COW - 引流产品（高销量、低利润）
     * DOG - 瘦狗产品（低销量、低利润）
     */
    private String quadrantType;

    /**
     * 象限描述
     */
    private String quadrantDesc;

    /**
     * 战略建议
     */
    private String strategySuggestion;
}
