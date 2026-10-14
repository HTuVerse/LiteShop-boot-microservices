package org.hongtu.ai.model;

/** AI 经营诊断后台生成的提交结果，前端据此轮询诊断记录。 */
public record AiDashboardSubmission(String requestId, String status) {
}
