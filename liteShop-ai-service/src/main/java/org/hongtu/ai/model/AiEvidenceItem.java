package org.hongtu.ai.model;

/**
 * 模型返回的一条经营证据。
 *
 * @param level  严重程度：INFO 信息，WARNING 需关注，OPPORTUNITY 机会
 * @param title  证据标题
 * @param detail 证据说明，数字必须来自给定数据
 * @param metricCode 指标目录中的稳定编码；无法对应时留空
 */
public record AiEvidenceItem(String level, String title, String detail, String metricCode) {
}
