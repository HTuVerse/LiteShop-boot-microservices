package org.hongtu.saas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 面向套餐对比页的基础功能展示项。
 *
 * <p>这些能力对所有套餐一致开放，不参与运行时权限计算；真正可售且由服务端强制执行的
 * 差异权益仍保存在 saas_feature / saas_plan_entitlement。</p>
 */
@Data
@Accessors(chain = true)
@TableName("saas_display_feature")
public class SaasDisplayFeature {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String moduleCode;
    private String moduleName;
    private String featureCode;
    private String featureName;
    private String description;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
