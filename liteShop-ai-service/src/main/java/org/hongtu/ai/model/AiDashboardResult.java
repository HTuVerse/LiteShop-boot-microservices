package org.hongtu.ai.model;

import java.util.List;

/**
 * AI 经营诊断仪表盘的模型结构化结果。模型先用只读工具取数，再据此自适应组织卡片与图表。
 *
 * @param headline 一句话总体诊断
 * @param report   结合本次真实数据的针对性诊断报告
 * @param score    综合诊断评分，可空；旧快照无此字段时按降级处理
 * @param cards    自适应卡片集合，类型、数量与顺序由模型决定
 */
public record AiDashboardResult(String headline, String report, AiDashboardScore score,
                                List<AiDashboardCard> cards) {

    /** 兼容仍按旧三参构造的调用方（无评分）。 */
    public AiDashboardResult(String headline, String report, List<AiDashboardCard> cards) {
        this(headline, report, null, cards);
    }
}
