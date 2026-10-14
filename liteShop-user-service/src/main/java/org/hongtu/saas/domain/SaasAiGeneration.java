package org.hongtu.saas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** AI 店长生成操作的持久化预占和结果。 */
@Data
@Accessors(chain = true)
@TableName("saas_ai_generation")
public class SaasAiGeneration {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String requestId;
    private String requestHash;
    private String operation;
    private Long createdBy;
    private String scopeType;
    private Long storeId;
    private String timeRange;
    private String periodKey;
    private String status;
    private String failureCode;
    private String failureMessage;
    private Boolean retryable;
    private Boolean quotaCharged;
    private Long sessionId;
    private String inputText;
    private String referenceRequestId;
    private String feedback;
    private LocalDateTime feedbackAt;
    private String responseJson;
    private Long promptTokens;
    private Long completionTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
