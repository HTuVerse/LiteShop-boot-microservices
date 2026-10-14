package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抽奖发奖发件箱。与中奖记录同一事务写入，提交后再投递。
 */
@Data
@TableName("lottery_prize_outbox")
public class LotteryPrizeOutbox {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long tenantId;

    private String eventId;

    private Long recordId;

    /** COUPON 或 POINTS。 */
    private String prizeType;

    private String payload;

    /** PENDING、SENT、DONE、FAILED。 */
    private String status;

    private Integer attempts;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
