package org.hongtu.ai.model;

import org.hongtu.api.dto.AiAdvisorSessionDTO;

/**
 * 经营顾问回答及其持久化会话。
 *
 * @param requestId 本轮问答的请求ID，前端据此提交反馈，无需再次查询消息列表
 * @param session   持久化后的会话
 * @param result    本轮经营诊断结果
 */
public record AiAdvisorReply(String requestId, AiAdvisorSessionDTO session, AiManagerResponse result) {
}
