package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单加菜、退菜不可变事件日志。
 */
@Data
@Accessors(chain = true)
@TableName("order_dish_change_log")
public class OrderDishChangeLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 事件幂等键。 */
    private String eventKey;

    private Long orderId;
    private Long orderItemId;
    private Long storeId;
    private Long dishId;
    private Long categoryId;

    /** 1-加菜，2-退菜。 */
    private Integer changeType;

    private Integer quantity;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime occurredAt;
}
