package org.hongtu.ai.model;

import java.util.List;

/** 智能问数可选的排行、原因或图表卡片。 */
public record ChatCard(String type, String title, String period, String metricCode,
                       List<ChatCardItem> items, AiDashboardChart chart) {
}
