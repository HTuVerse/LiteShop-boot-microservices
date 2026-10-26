package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("sys_tenant")
public class SysTenant implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private Integer status;

    /** 当前是否可进入商户工作空间，仅供列表展示，不持久化。 */
    @TableField(exist = false)
    private Boolean accessAvailable;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
