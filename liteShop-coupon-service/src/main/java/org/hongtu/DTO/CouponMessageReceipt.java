package org.hongtu.DTO;

import lombok.Data;

/** 已提交的优惠券消息凭据，用于重试通知抽奖服务。 */
@Data
public class CouponMessageReceipt {
    /** 发券业务事件 ID。 */
    private String eventId;
    /** 所属租户 ID。 */
    private Long tenantId;
}
