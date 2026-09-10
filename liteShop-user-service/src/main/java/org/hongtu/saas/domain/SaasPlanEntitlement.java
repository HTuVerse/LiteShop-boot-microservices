package org.hongtu.saas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("saas_plan_entitlement")
public class SaasPlanEntitlement {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long planVersionId;
    private Long featureId;
    private Integer enabled;
    private BigDecimal quotaValue;
    private String textValue;
    private Integer unlimited;
    private String resetPeriod;
    private String enforcement;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
