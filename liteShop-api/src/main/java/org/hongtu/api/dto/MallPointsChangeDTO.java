package org.hongtu.api.dto;

import lombok.Data;

/** 积分商城扣减及补偿入账参数。 */
@Data
public class MallPointsChangeDTO {
    private Long userId;
    /** 正整数积分数量。 */
    private Integer points;
    /** 积分商城兑换订单ID。 */
    private Long orderId;
    /** SPEND 扣减，REFUND 退回。 */
    private String action;
}
