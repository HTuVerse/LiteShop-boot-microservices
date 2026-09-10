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
@TableName("saas_usage_counter")
public class SaasUsageCounter {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String featureCode;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private BigDecimal usedValue;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
