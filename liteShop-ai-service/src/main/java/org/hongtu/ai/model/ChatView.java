package org.hongtu.ai.model;

import java.util.List;

/** 智能问数由前端固定组件渲染的富回复。 */
public record ChatView(String tip, List<ChatCard> cards, List<String> followUps) {
}
