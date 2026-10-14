package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.PricingEnums.ActionType;
import org.hongtu.restaurant.enums.PricingEnums.ConditionType;
import org.hongtu.restaurant.enums.PricingEnums.GroupingType;
import org.hongtu.restaurant.enums.PricingEnums.RepeatMode;

import java.io.Serializable;
import java.math.BigDecimal;

/** 定价活动的可执行条件与动作。 */
@Data
@Accessors(chain = true)
@TableName("pricing_rule")
public class PricingRule implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long campaignId;
    /** 0始终生效，1数量条件，2订单金额条件。 */
    private ConditionType conditionType;
    private Integer minimumQuantity;
    private BigDecimal minimumAmount;
    /** 数量活动优惠第几件。 */
    private Integer nthQuantity;
    /** 0同菜品，1分类，2任意商品。当前结算完整支持同菜品。 */
    private GroupingType groupingType;
    /** 0固定价，1折扣率，2直减，3赠送。 */
    private ActionType actionType;
    private BigDecimal actionValue;
    /** 0仅一次，1每组重复。 */
    private RepeatMode repeatMode;
}
