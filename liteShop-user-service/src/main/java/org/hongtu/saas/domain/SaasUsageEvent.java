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
@TableName("saas_usage_event")
public class SaasUsageEvent {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String featureCode;
    private BigDecimal amount;
    private String eventType;
    private String idempotencyKey;
    private LocalDateTime periodStart;
    private String status;
    private LocalDateTime createTime;
}
