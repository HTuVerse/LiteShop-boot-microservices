package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 会员经验实际增减流水。 */
@Data
@Accessors(chain = true)
@TableName("user_exp_record")
public class UserExpRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private Long userId;
    /** 正数为增加，负数为减少。 */
    private Integer changeExp;
    private Integer beforeExp;
    private Integer afterExp;
    /** ORDER_REWARD、ORDER_REFUND、MANUAL、LEVEL_ADJUST、OPENING_BALANCE。 */
    private String source;
    private String orderNo;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operationTime;
}
