package org.hongtu.restaurant.VO.analysis;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 滞销菜品VO
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class SlowMovingDishVO {

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
     * 统计期间销量
     */
    private Integer salesQuantity;

    /**
     * 统计期间销售额
     */
    private BigDecimal salesAmount;

    /**
     * 近30天销量
     */
    private Integer salesLast30Days;

    /**
     * 近30天销售额
     */
    private BigDecimal salesAmountLast30Days;

    /**
     * 平均日销量
     */
    private Double avgDailySales;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 上架时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 最后一次销售时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastSaleTime;

    /**
     * 滞销天数
     */
    private Integer slowMovingDays;

    /**
     * 建议操作
     */
    private String suggestion;
}
