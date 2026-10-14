package org.hongtu.ai.model;

import java.time.LocalDateTime;

/** 前端用于恢复状态和查看历史的 AI 店长分析记录。 */
public record AiManagerRecord(
        String requestId,
        String operation,
        String status,
        String scopeType,
        Long storeId,
        String timeRange,
        String periodKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String failureCode,
        String failureMessage,
        Boolean retryable,
        AiManagerResponse result
) {
}
