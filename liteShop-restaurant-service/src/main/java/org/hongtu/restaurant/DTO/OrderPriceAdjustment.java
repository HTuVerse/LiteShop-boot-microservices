package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.PricingEnums.AdjustmentType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单定价优惠快照，退款、审计与经营分析均以此为准。 */
@Data
@Accessors(chain = true)
@TableName("order_price_adjustment")
public class OrderPriceAdjustment implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long campaignId;
    private AdjustmentType adjustmentType;
    private BigDecimal beforeAmount;
    /** 正数表示减免金额。 */
    private BigDecimal adjustmentAmount;
    private BigDecimal afterAmount;
    private String ruleSnapshot;
    private LocalDateTime createdAt;
}
