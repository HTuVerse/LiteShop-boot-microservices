package org.hongtu.ai.model;

import java.util.List;

/**
 * 经营简报的模型结构化结果。
 *
 * @param keyPoints  AI 归纳的经营结论要点
 * @param evidence   支撑结论的证据
 * @param todayTasks 今天应处理的事项，仅草稿
 */
public record BriefingResult(List<AiTitledDetail> keyPoints,
                             List<AiEvidenceItem> evidence,
                             List<AiActionItem> todayTasks) {
}
