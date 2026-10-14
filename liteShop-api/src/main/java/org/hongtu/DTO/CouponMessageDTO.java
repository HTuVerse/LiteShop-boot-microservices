package org.hongtu.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hongtu.enums.CouponMsgOperateTypeEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券消息DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务事件唯一键，消费者据此保证幂等。 */
    private String eventId;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 操作类型：0-发放，1-过期
     */
    private CouponMsgOperateTypeEnum operationType;

    /**
     * 操作时间
     */
    private LocalDateTime startTime;

    private LocalDateTime expiredTime;

    /**
     * 额外信息（JSON格式）
     */
    private String extraInfo;
}
