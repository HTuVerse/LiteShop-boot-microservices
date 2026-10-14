package org.hongtu.restaurant.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hongtu.enums.CouponTypeEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券计算结果VO
 *
 * @author Ht
 * @since 2025-02-13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponDiscountResultVO {

    /**
     * 是否可用
     */
    private Boolean usable;

    /**
     * 不可用原因（usable=false时）
     */
    private String reason;

    /**
     * 总优惠金额
     */
    private BigDecimal totalDiscountAmount;

    /**
     * 优惠券明细列表
     */
    private List<CouponDetailVO> couponDetails;

    /**
     * 优惠券明细VO（内部类）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponDetailVO {

        /**
         * 用户优惠券ID
         */
        private Long userCouponId;

        /**
         * 优惠券ID
         */
        private Long couponId;

        /**
         * 优惠券名称
         */
        private String couponName;

        /**
         * 优惠券类型
         */
        private CouponTypeEnum couponType;

        /**
         * 优惠券类型描述
         */
        private String couponTypeDesc;

        /**
         * 优惠金额
         */
        private BigDecimal discountAmount;

        /**
         * 券面值/折扣率
         */
        private BigDecimal value;

        /**
         * 最低消费金额
         */
        private BigDecimal minAmount;

        /**
         * 生效时间
         */
        private LocalDateTime startTime;

        /**
         * 过期时间
         */
        private LocalDateTime expireTime;

        /**
         * 关联菜品列表（仅菜品免单券）
         */
        private List<DishItemVO> dishItems;
    }

    /**
     * 菜品信息VO（内部类，用于优惠券关联菜品）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DishItemVO {

        /**
         * 菜品ID
         */
        private Long dishId;

        /**
         * 菜品名称
         */
        private String dishName;

        /**
         * 菜品图片
         */
        private String dishPic;

        /** 门店覆盖后的正常售价。 */
        private BigDecimal salePrice;

        /** 当前活动计算后的价格。 */
        private BigDecimal currentPrice;
    }
}
