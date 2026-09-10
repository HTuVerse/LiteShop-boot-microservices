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
@TableName("saas_addon_entitlement")
public class SaasAddonEntitlement {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long addonId;
    private Long featureId;
    private String operation;
    private BigDecimal quotaValue;
    private String textValue;
    private LocalDateTime createTime;
}
