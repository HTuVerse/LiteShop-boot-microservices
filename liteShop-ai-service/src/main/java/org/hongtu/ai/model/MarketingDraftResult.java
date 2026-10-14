package org.hongtu.ai.model;

/**
 * 营销方案草稿的模型结构化结果。字段只描述方案，不表示活动已创建。
 *
 * @param summary          方案结论
 * @param audience         目标人群
 * @param mechanism        优惠机制
 * @param budgetConstraint 预算约束
 * @param risk             风险
 * @param reviewMetrics    复盘指标
 */
public record MarketingDraftResult(
        String summary,
        String audience,
        String mechanism,
        String budgetConstraint,
        String risk,
        String reviewMetrics
) {
}
