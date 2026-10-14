package org.hongtu.ai.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 店长请求参数。
 */
public class AiManagerRequest {

    /** 一次生成操作的 UUID；网络重试必须复用。 */
    @NotBlank
    private String requestId;

    /** 经营范围：TENANT 租户总部、STORE 单门店。 */
    private String scopeType = "TENANT";

    /** 门店范围下的门店ID。 */
    private Long storeId;

    /** 时间范围：TODAY、YESTERDAY、WEEK、MONTH。 */
    private String timeRange = "TODAY";

    /** 对话问题或营销目标。 */
    @Size(max = 1000, message = "问题不能超过1000个字符")
    private String message;

    /** 营销预算上限，单位：元。 */
    @DecimalMin(value = "0.00", message = "营销预算不能小于0")
    private BigDecimal budget;

    /** 同一经营范围下最近几条对话，仅帮助理解追问，不作为事实或权限来源。 */
    @Valid
    @Size(max = 6, message = "最多携带6条历史消息")
    private List<ConversationMessage> history = List.of();

    /** 服务端写入的发起用户ID，后台任务恢复鉴权上下文时使用。 */
    private Long createdBy;
    /** 经营顾问持久化会话ID。 */
    private Long sessionId;
    /** 从某次经营诊断继续咨询时携带的请求ID。 */
    private String referenceRequestId;

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public List<ConversationMessage> getHistory() {
        return history;
    }

    public void setHistory(List<ConversationMessage> history) {
        this.history = history;
    }

    /** 有界对话上下文，避免历史消息造成无限输入增长。 */
    public record ConversationMessage(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 1000) String content
    ) {
    }

    public String getRequestId() { return requestId; }

    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Long getCreatedBy() { return createdBy; }

    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getSessionId() { return sessionId; }

    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getReferenceRequestId() { return referenceRequestId; }

    public void setReferenceRequestId(String referenceRequestId) { this.referenceRequestId = referenceRequestId; }
}
