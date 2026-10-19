package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 打印任务持久化记录，用于回调幂等、失败追踪和人工补打。 */
@Data
@TableName("print_job")
public class PrintJob {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long tenantId;

    private Long orderId;
    private Long storeId;
    private Long printerId;
    private String printerSn;
    /** 打印机、订单和票据类型组成的幂等键。 */
    private String jobKey;
    /** 待发送的小票内容。订单事务只落库，不在事务里调用打印机。 */
    private String content;
    /** 该任务应打印的联数，保存模板快照，重试时保持一致。 */
    private Integer printCopies;
    private String providerOrderId;
    /** 0-已提交，1-打印成功，2-打印失败，3-提交失败，4-提交中，5-待发送。 */
    private Integer status;
    private Integer attempts;
    private String lastError;
    private LocalDateTime callbackTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
