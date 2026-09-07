package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.PrizeTypeEnum;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 抽奖奖品表
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("lottery_prize")
public class LotteryPrize implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 奖品ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 奖品名称
     */
    private String name;

    /**
     * 奖品图片URL
     */
    private String imgUrl;

    /**
     * 中奖概率(百分比)
     */
    private BigDecimal probability;

    /**
     * 奖品类型: 1-优惠券, 2-积分, 3-实物, 4-谢谢参与
     */
    private PrizeTypeEnum type;

    /**
     * 关联的优惠券ID（使用包装类型，非优惠券奖品时可为null）
     */
    private Long couponId;

    /**
     * 积分数量
     */
    private Integer points;

    /**
     * 是否启用[0:禁用；1:启用]
     */
    private BooleanEnum enabled;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;


}
