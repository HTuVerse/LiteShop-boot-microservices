package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 优惠券剩余可发库存的人工调整记录。 */
@Data
@TableName("coupon_stock_adjustment")
public class CouponStockAdjustment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long couponId;
    /** 正数补库，负数减库。 */
    private Integer delta;
    private Integer beforeCount;
    private Integer afterCount;
    private Long operatorId;
    private String reason;
    private LocalDateTime createTime;
    private Long tenantId;
}
