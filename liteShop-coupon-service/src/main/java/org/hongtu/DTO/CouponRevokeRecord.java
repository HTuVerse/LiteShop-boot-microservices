package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户券回收审计记录，每张回收的券保留一条。 */
@Data
@TableName("coupon_revoke_record")
public class CouponRevokeRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userCouponId;
    private Long couponId;
    private Long userId;
    /** 操作管理员；系统在取消订单时回收为空。 */
    private Long operatorId;
    private String reason;
    /** 同一次模板批量回收共用的批次号。 */
    private String batchId;
    private LocalDateTime createTime;
    private Long tenantId;
}
