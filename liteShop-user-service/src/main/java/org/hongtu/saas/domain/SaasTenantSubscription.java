package org.hongtu.saas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("saas_tenant_subscription")
public class SaasTenantSubscription {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long planVersionId;
    private String billingCycle;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime trialEndAt;
    private LocalDateTime graceEndAt;
    private LocalDateTime cancelAt;
    private Integer cancelAtPeriodEnd;
    private Integer autoRenew;
    private String externalSubscriptionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
