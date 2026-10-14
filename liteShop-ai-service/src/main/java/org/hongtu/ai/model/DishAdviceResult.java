package org.hongtu.ai.model;

import java.util.List;

/**
 * 菜品经营建议的模型结构化结果。
 *
 * @param keyPoints         AI 归纳的菜品经营要点
 * @param hotOpportunities  热销机会
 * @param slowMovingRisks   滞销风险
 * @param insufficientData  样本不足、不能下结论的项
 * @param menuSuggestions   需人工确认的菜单建议
 */
public record DishAdviceResult(
        List<AiTitledDetail> keyPoints,
        List<AiTitledDetail> hotOpportunities,
        List<AiTitledDetail> slowMovingRisks,
        List<AiTitledDetail> insufficientData,
        List<AiActionItem> menuSuggestions
) {
}
