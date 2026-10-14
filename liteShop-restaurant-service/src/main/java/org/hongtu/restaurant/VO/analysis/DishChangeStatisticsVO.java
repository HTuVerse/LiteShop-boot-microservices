package org.hongtu.restaurant.VO.analysis;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品加菜、退菜统计。
 */
@Data
public class DishChangeStatisticsVO {
    private Long dishId;
    private String dishName;
    private Long categoryId;
    private String categoryName;
    /** 1-加菜，2-退菜。 */
    private Integer changeType;
    /** 退菜模式下的退菜原因。 */
    private String reason;
    private Integer orderCount;
    private Integer changeCount;
    private Integer quantity;
    private BigDecimal amount;
}
