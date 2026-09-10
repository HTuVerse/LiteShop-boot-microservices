package org.hongtu.saas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("saas_plan")
public class SaasPlan {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String planCode;
    private String planName;
    private String targetCustomer;
    private String coreBenefits;
    private String description;
    private Integer recommended;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
