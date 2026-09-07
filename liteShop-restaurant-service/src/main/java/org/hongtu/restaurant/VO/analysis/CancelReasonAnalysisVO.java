package org.hongtu.restaurant.VO.analysis;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单取消原因分析VO
 *
 * @author Ht
 * @since 2025-01-30
 */
@Data
public class CancelReasonAnalysisVO {

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 取消次数
     */
    private Integer cancelCount;

    /**
     * 占比（%）
     */
    private BigDecimal percentage;
}
