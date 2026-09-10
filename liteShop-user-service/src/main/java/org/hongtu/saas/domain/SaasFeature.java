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
@TableName("saas_feature")
public class SaasFeature {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String featureCode;
    private String featureName;
    private String featureType;
    private String unit;
    private String quotaMode;
    private String usageSource;
    private String usageMetric;
    private BigDecimal defaultValue;
    private String defaultTextValue;
    private String allowedValues;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
