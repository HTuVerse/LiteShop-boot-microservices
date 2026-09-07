package org.hongtu.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.enums.VerifyStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销结果/预览 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResultVO {

    /**
     * 核销记录ID（核销成功后返回）
     */
    private Long verifyRecordId;

    /**
     * 用户优惠券ID
     */
    private Long userCouponId;

    /**
     * 优惠券唯一码
     */
    private String couponCode;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 优惠券名称
     */
    private String couponName;

    /**
     * 优惠券描述
     */
    private String couponDescription;

    /**
     * 优惠券类型
     * 0:满减券, 1:整单折扣券, 2:菜品免单券
     */
    private CouponTypeEnum couponType;

    /**
     * 优惠券面值
     */
    private BigDecimal couponValue;

    /**
     * 最低消费金额
     */
    private BigDecimal minAmount;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名/手机号（脱敏）
     */
    private String userName;

    /**
     * 优惠券状态
     */
    private CouponStatusEnum couponStatus;

    /**
     * 核销状态
     */
    private VerifyStatusEnum verifyStatus;

    /**
     * 优惠券生效时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 优惠券过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;

    /**
     * 核销时间（核销成功后返回）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime verifyTime;

    /**
     * 是否可核销
     */
    private Boolean canVerify;

    /**
     * 不可核销原因
     */
    private String verifyMessage;
}
