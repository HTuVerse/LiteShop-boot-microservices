package org.hongtu.restaurant.VO.analysis;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 菜品销售趋势VO
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class DishSalesTrendVO {

    /**
     * 日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate date;

    /**
     * 销售数量
     */
    private Integer salesQuantity;

    /**
     * 销售额
     */
    private BigDecimal salesAmount;
}
