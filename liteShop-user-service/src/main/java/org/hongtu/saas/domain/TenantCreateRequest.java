package org.hongtu.saas.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 平台创建租户时一次性提交商户资料和首个套餐订阅。 */
@Data
public class TenantCreateRequest {
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private String remark;
    private Long planVersionId;
    private String billingCycle;
    private String subscriptionStatus;
    private Integer autoRenew;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime currentPeriodStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime currentPeriodEnd;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime trialEndAt;
}
