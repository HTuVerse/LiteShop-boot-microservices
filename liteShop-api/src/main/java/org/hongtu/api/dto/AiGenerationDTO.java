package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** AI 店长一次生成操作的预占、结果和用量数据。 */
@Data
@Accessors(chain = true)
public class AiGenerationDTO {
    /** 同一操作重试时保持不变的ID。 */
    private String requestId;
    /** 操作类型与参数的 SHA-256 摘要。 */
    private String requestHash;
    /** BRIEFING、DISH_ADVICE、MARKETING_DRAFT 或 CHAT。 */
    private String operation;
    /** 发起生成的用户ID，仅用于记录来源。 */
    private Long createdBy;
    /** 经营范围：TENANT 或 STORE。 */
    private String scopeType;
    /** 单门店范围对应的门店ID。 */
    private Long storeId;
    /** 时间类型：TODAY、YESTERDAY、WEEK、MONTH。 */
    private String timeRange;
    /** 服务端计算的实际周期键，防止跨日、跨周或跨月串用结果。 */
    private String periodKey;
    /** PROCESSING、SUCCEEDED 或 FAILED。 */
    private String status;
    /** 可安全展示的失败分类，不包含供应商响应原文。 */
    private String failureCode;
    /** 可安全展示给用户的失败原因。 */
    private String failureMessage;
    /** 是否适合由用户重新发起。 */
    private Boolean retryable;
    /** 本次调用是否成功取得生成执行权。 */
    private boolean acquired;
    /** 是否单独扣减额度；完整诊断内的子分析可共用父操作额度。 */
    private Boolean quotaCharged;
    /** 经营顾问会话ID，仅 CHAT 使用。 */
    private Long sessionId;
    /** 用户输入，仅用于恢复会话，不记录工具上下文。 */
    private String inputText;
    /** 关联的诊断或方案请求ID。 */
    private String referenceRequestId;
    /** 用户对本次建议的有用性反馈。 */
    private String feedback;
    private LocalDateTime feedbackAt;
    /** 成功结果的 JSON，用于重试直接返回。 */
    private String responseJson;
    private Long promptTokens;
    private Long completionTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
