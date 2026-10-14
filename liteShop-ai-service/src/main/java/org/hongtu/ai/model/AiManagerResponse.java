package org.hongtu.ai.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 店长分析结果。
 *
 * @param type           结果类型：BRIEFING、DISH_ADVICE、MARKETING_DRAFT、CHAT
 * @param title          结果标题
 * @param content        可直接展示的经营结论
 * @param keyPoints      AI 归纳的结构化结论要点
 * @param scope          实际生效的经营范围
 * @param evidence       支撑结论的经营证据
 * @param actions        建议动作，第一阶段仅生成草稿而不直接执行
 * @param modelGenerated 是否由模型增强生成
 * @param generatedAt    生成时间
 * @param dashboard      AI 经营诊断仪表盘结构，仅动态诊断结果填充，其它类型为空
 * @param chatView       智能问数富回复结构，仅 CHAT 类型填充
 */
public record AiManagerResponse(
        String type,
        String title,
        String content,
        List<SummaryPoint> keyPoints,
        Map<String, Object> scope,
        List<Evidence> evidence,
        List<SuggestedAction> actions,
        boolean modelGenerated,
        LocalDateTime generatedAt,
        AiDashboardResult dashboard,
        ChatView chatView
) {
    /** 兼容仍使用旧字段构造结果的调用方。 */
    public AiManagerResponse(String type, String title, String content, List<SummaryPoint> keyPoints,
                             Map<String, Object> scope, List<Evidence> evidence,
                             List<SuggestedAction> actions, boolean modelGenerated,
                             LocalDateTime generatedAt, AiDashboardResult dashboard) {
        this(type, title, content, keyPoints, scope, evidence, actions, modelGenerated,
                generatedAt, dashboard, null);
    }

    /** 可由前端逐项渲染的经营结论。 */
    public record SummaryPoint(String title, String detail) {
    }

    /**
     * 可追溯的经营证据卡片。指标值字段允许为空，但来源页面和数据截止时间由服务端补充，
     * 不采信模型自行编造的来源信息。
     */
    public record Evidence(
            String level,
            String title,
            String detail,
            String metricCode,
            String metricName,
            Object value,
            String unit,
            String periodStart,
            String periodEnd,
            Object comparisonValue,
            BigDecimal changeRate,
            Long sampleSize,
            LocalDateTime sourceUpdatedAt,
            String sourcePage
    ) {
    }

    /** 第一阶段经营动作草稿。 */
    public record SuggestedAction(String title, String description, String risk, boolean requiresConfirmation) {
    }
}
