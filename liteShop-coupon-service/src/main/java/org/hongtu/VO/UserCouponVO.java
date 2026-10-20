package org.hongtu.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.CouponTypeEnum;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户优惠券VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponVO {

    /**
     * 用户优惠券ID
     */
    private Long id;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 优惠券名称
     */
    private String name;

    /**
     * 优惠券描述
     */
    private String description;

    /**
     * 优惠券类型
     * 0:满减券, 1:整单折扣券, 2:菜品免单券
     */
    private CouponTypeEnum type;

    /** 适用门店，空表示全部门店。 */
    private String storeIds;

    /** 可用星期，1周一至7周日；空表示每天。 */
    private String useWeekdays;

    /** 每日可用时段，空表示全天。 */
    private java.time.LocalTime useStartTime;
    private java.time.LocalTime useEndTime;

    /**
     * 面值(折扣券存折扣率0.8)
     */
    private BigDecimal value;

    /**
     * 最低消费金额
     */
    private BigDecimal minAmount;

    /**
     * 状态(0:未生效,1:未使用,2:已使用,3:已过期)
     */
    private CouponStatusEnum status;

    /**
     * 有效期开始
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 有效期结束
     */
    private LocalDateTime expireTime;

    /** 券模板的有效期，可能比用户领券有效期更短。 */
    private LocalDateTime templateStartTime;
    private LocalDateTime templateEndTime;
    /**
     * 优惠券唯一码
     */
    private String code;

    /**
     * 关联的菜品列表（菜品折扣券、菜品免单券、套餐券、团购券使用）
     */
    private List<CouponDishItem> dishItems;

    /**
     * 是否可叠加使用（0-不可叠加，1-可叠加）
     */
    private BooleanEnum stackable;

    /**
     * 互斥组ID（同组互斥）
     */
    private String exclusiveGroup;

    /** 当前券可与哪些券模板叠加；是否真正可用仍以订单试算为准。 */
    private List<Long> stackWithCouponIds;

    /** 面向顾客展示的可叠加券名称。 */
    private List<String> stackWithCouponNames;
}
