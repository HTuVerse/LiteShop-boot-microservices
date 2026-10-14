package org.hongtu.ai.model;

/**
 * AI 店长统一指标定义。
 *
 * @param code             稳定指标编码
 * @param name             展示名称
 * @param unit             展示单位
 * @param domain           业务数据域
 * @param description      指标口径说明
 * @param desiredDirection 经营改善的期望方向
 * @param reviewable       是否可从现有聚合数据自动复盘
 * @param tenantSourcePage 总部视图来源页
 * @param storeSourcePage  单店视图来源页
 */
public record AiMetricDefinition(
        String code,
        String name,
        String unit,
        String domain,
        String description,
        String desiredDirection,
        boolean reviewable,
        String tenantSourcePage,
        String storeSourcePage
) {
}
