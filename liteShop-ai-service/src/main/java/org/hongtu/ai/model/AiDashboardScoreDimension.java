package org.hongtu.ai.model;

/**
 * 诊断评分的单个维度子项，用于在雷达图或进度条上呈现分项表现。
 *
 * @param name    维度名称，如营收、效率、风险、增长
 * @param score   维度得分，取值 0-100，由服务端裁剪
 * @param comment 维度一句点评，可空
 */
public record AiDashboardScoreDimension(String name, Integer score, String comment) {
}
