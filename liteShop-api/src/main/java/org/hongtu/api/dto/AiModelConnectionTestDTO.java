package org.hongtu.api.dto;

/** AI 模型接口连通性测试结果。 */
public record AiModelConnectionTestDTO(boolean connected, long elapsedMillis) {
}
