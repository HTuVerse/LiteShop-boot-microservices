package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 菜品销售排行VO
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class DishSalesRankingVO {

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 菜品名称
     */
    private String dishName;

    /**
     * 菜品分类ID
     */
    private Long categoryId;

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
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 利润
     */
    private BigDecimal profit;

    /**
     * 利润率（百分比）
     */
    private BigDecimal profitRate;

    /**
     * 排名
     */
    private Integer ranking;

    /**
     * 库存数量
     */
    private Integer stock;
}
