package org.hongtu.restaurant.VO.analysis;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单菜每日销售趋势。
 */
@Data
public class DishSalesTrendVO {
    private LocalDate date;
    private Integer salesQuantity;
    private BigDecimal salesAmount;
}
