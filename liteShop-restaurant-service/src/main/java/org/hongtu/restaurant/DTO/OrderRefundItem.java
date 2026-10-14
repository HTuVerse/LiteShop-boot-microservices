package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款明细。成功入账前订单项数量保持不变。
 */
@Data
@TableName("order_refund_item")
public class OrderRefundItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long refundId;
    private Long orderItemId;
    private Long dishId;
    /** 退菜数量。 */
    private Integer quantity;
    /** 该行退款金额，单位：元。 */
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
