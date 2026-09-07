package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单状态分布VO
 * 用于饼图展示各状态订单占比
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class OrderStatusDistributionVO {

    /**
     * 状态名称（已完成/已取消/进行中等）
     */
    private String statusName;

    /**
     * 状态码
     */
    private Integer statusCode;

    /**
     * 订单数量
     */
    private Integer orderCount;

    /**
     * 占比（%）
     */
    private BigDecimal percentage;

    /**
     * 销售额
     */
    private BigDecimal sales;
}
