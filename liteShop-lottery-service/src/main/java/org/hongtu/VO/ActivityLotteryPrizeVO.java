package org.hongtu.VO;

import lombok.Data;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.PrizeTypeEnum;

import java.math.BigDecimal;

/**
 * 功能：
 * 作者：
 * 时间：2025/8/7 上午2:11
 */
@Data
public class ActivityLotteryPrizeVO {

    private Long id;
    private String name;
    private String imgUrl;
    private PrizeTypeEnum type;
    private BooleanEnum enabled;
    /**
     * 中奖概率（注意：面向用户时建议隐藏此字段）
     */
    private BigDecimal probability;
    /**
     * 关联的优惠券ID（使用包装类型，非优惠券奖品时可为null）
     */
    private Long couponId;
    private Integer points;
    private Long totalStock;
    private Long surplusStock;

}
