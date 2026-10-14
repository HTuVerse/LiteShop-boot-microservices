package org.hongtu.ai.model;

/** AI 店长经营简报和菜品建议的后台提交结果。 */
public record AiInsightSubmission(String requestId, String briefingRequestId,
                                  String dishAdviceRequestId, String status) {
}
