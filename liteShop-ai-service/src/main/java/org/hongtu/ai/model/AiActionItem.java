package org.hongtu.ai.model;

/**
 * 模型返回的一条建议动作。是否需要人工确认由服务端固定，不交给模型决定。
 *
 * @param title       动作标题
 * @param description 动作说明
 * @param risk        风险提示
 */
public record AiActionItem(String title, String description, String risk) {
}
