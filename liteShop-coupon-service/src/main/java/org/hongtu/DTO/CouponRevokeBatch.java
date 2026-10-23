package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 模板回收任务，支持服务重启后按批继续处理。 */
@Data
@TableName("coupon_revoke_batch")
public class CouponRevokeBatch {
    @TableId(type = IdType.INPUT)
    private String batchId;
    private Long tenantId;
    private Long couponId;
    private Long operatorId;
    private String reason;
    /** 0 处理中，1 已完成。 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
