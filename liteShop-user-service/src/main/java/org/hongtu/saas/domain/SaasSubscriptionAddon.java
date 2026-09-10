package org.hongtu.saas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("saas_subscription_addon")
public class SaasSubscriptionAddon {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long subscriptionId;
    private Long addonId;
    private Integer quantity;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String externalItemId;
    /** Purchase-time copy of add-on rules so later catalog edits cannot change sold rights. */
    private String entitlementSnapshot;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
