package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 餐厅服务本地发件箱。远程写操作先落库，再由对账任务确认或补偿。
 */
@Data
@TableName("local_outbox")
public class LocalOutbox {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long tenantId;

    /** 业务幂等键，同一事件重复投递不得产生第二次副作用。 */
    private String eventId;

    /** COUPON_VERIFY、COUPON_RESTORE、BALANCE_CHANGE、MEMBER_REWARD_SYNC。 */
    private String eventType;

    private String payload;

    /** PENDING、SENT、UNKNOWN、DONE、COMPENSATED、NEEDS_REVIEW。 */
    private String status;

    /** 外层业务事务提交时置为 true，对账不能仅凭订单存在推断提交成功。 */
    private Boolean committed;

    private Integer attempts;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
