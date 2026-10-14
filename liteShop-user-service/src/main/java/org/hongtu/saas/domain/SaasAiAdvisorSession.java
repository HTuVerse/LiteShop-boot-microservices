package org.hongtu.saas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 经营顾问持久化会话。 */
@Data
@Accessors(chain = true)
@TableName("saas_ai_advisor_session")
public class SaasAiAdvisorSession {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String title;
    private String scopeType;
    private Long storeId;
    private String status;
    private String diagnosisRequestId;
    private String initialRequestId;
    private Long createdBy;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
