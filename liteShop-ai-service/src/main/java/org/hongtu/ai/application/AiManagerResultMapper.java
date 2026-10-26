package org.hongtu.ai.application;

import org.hongtu.ai.model.AiActionItem;
import org.hongtu.ai.model.AiDashboardCard;
import org.hongtu.ai.model.AiDashboardChart;
import org.hongtu.ai.model.AiDashboardChartSeries;
import org.hongtu.ai.model.AiDashboardResult;
import org.hongtu.ai.model.AiDashboardScore;
import org.hongtu.ai.model.AiDashboardScoreDimension;
import org.hongtu.ai.model.AiDashboardTable;
import org.hongtu.ai.model.AiDashboardTableColumn;
import org.hongtu.ai.model.AiEvidenceItem;
import org.hongtu.ai.model.AiManagerResponse;
import org.hongtu.ai.model.AiMetricDefinition;
import org.hongtu.ai.model.AiTitledDetail;
import org.hongtu.ai.model.BriefingResult;
import org.hongtu.ai.model.ChatAnswerResult;
import org.hongtu.ai.model.ChatCard;
import org.hongtu.ai.model.ChatCardItem;
import org.hongtu.ai.model.ChatView;
import org.hongtu.ai.model.DishAdviceResult;
import org.hongtu.ai.model.MarketingDraftResult;
import org.hongtu.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 把模型结构化结果映射为前端已有的 AI 店长响应。动作一律需要人工确认。
 */
final class AiManagerResultMapper {
    private static final int MAX_ITEMS = 8;
    private static final int MAX_TEXT = 2000;
    // 结构化要点保持精炼，超长时裁剪并补省略号，避免前端一屏信息过载。
    private static final int MAX_POINT_TITLE = 24;
    private static final int MAX_POINT_DETAIL = 60;
    // 仪表盘卡片与图表上限，防止模型一次返回过多内容拖垮前端渲染。
    private static final int MAX_CARDS = 12;
    private static final int MAX_CARD_TITLE = 30;
    private static final int MAX_HEADLINE = 40;
    private static final int MAX_SERIES = 8;
    private static final int MAX_CATEGORY = 20;
    // 表格列行上限与评分维度上限，防止模型返回超大表格或过多维度拖垮前端。
    private static final int MAX_TABLE_COLUMNS = 6;
    private static final int MAX_TABLE_ROWS = 10;
    private static final int MAX_SCORE_DIMENSIONS = 6;
    private static final int MAX_TABLE_CELL = 40;
    // 数值单元格校验：允许负号、千分位与小数。
    private static final String NUMERIC_CELL = "-?(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d+)?%?";
    private static final int MAX_CHAT_CARDS = 4;
    private static final int MAX_CHAT_ITEMS = 5;
    private static final int MAX_FOLLOW_UPS = 3;
    private static final Set<String> CHAT_ICONS = Set.of("ri:group-line", "ri:shopping-cart-line",
            "ri:bar-chart-box-line", "ri:lightbulb-line", "ri:funds-line", "ri:store-2-line");

    private AiManagerResultMapper() {
    }

    static AiManagerResponse briefing(BriefingResult result, Map<String, Object> scope) {
        List<AiManagerResponse.SummaryPoint> keyPoints = summaryPoints(result == null ? null : result.keyPoints());
        requirePoints(keyPoints);
        return response("BRIEFING", "经营简报", "", keyPoints, scope,
                evidence(result.evidence(), scope), actions(result.todayTasks()));
    }

    static AiManagerResponse dishAdvice(DishAdviceResult result, Map<String, Object> scope) {
        List<AiManagerResponse.SummaryPoint> keyPoints = summaryPoints(result == null ? null : result.keyPoints());
        requirePoints(keyPoints);
        List<AiManagerResponse.Evidence> evidence = new ArrayList<>();
        addDetails(evidence, "OPPORTUNITY", result.hotOpportunities(), scope);
        addDetails(evidence, "WARNING", result.slowMovingRisks(), scope);
        addDetails(evidence, "INFO", result.insufficientData(), scope);
        return response("DISH_ADVICE", "菜品经营顾问", "", keyPoints, scope,
                evidence, actions(result.menuSuggestions()));
    }

    static AiManagerResponse marketingDraft(MarketingDraftResult result, Map<String, Object> scope) {
        requireText(result == null ? null : result.summary());
        String content = """
                %s

                目标人群：%s
                优惠机制：%s
                预算约束：%s
                风险：%s
                复盘指标：%s
                本方案仅为草稿，尚未创建活动。
                """.formatted(
                text(result.summary()),
                text(result.audience()),
                text(result.mechanism()),
                text(result.budgetConstraint()),
                text(result.risk()),
                text(result.reviewMetrics())).trim();
        List<AiManagerResponse.Evidence> evidence = new ArrayList<>();
        addTextEvidence(evidence, "INFO", "目标人群", result.audience(), scope);
        addTextEvidence(evidence, "INFO", "预算约束", result.budgetConstraint(), scope);
        addTextEvidence(evidence, "INFO", "复盘指标", result.reviewMetrics(), scope);
        List<AiManagerResponse.SuggestedAction> actions = List.of(new AiManagerResponse.SuggestedAction(
                "审批营销草稿", text(result.mechanism()), text(result.risk()), true));
        return response("MARKETING_DRAFT", "营销方案草稿", content, List.of(), scope, evidence, actions);
    }

    static AiManagerResponse chat(ChatAnswerResult result, Map<String, Object> scope) {
        requireText(result == null ? null : result.answer());
        // 聊天回答同样走结构化要点，前端据此渲染，避免只呈现一坨纯文本。
        List<AiManagerResponse.SummaryPoint> keyPoints = summaryPoints(result.keyPoints());
        String missing = text(result.missingData());
        String content = missing.isBlank() ? text(result.answer())
                : text(result.answer()) + "\n\n还缺少的数据：" + missing;
        List<AiManagerResponse.Evidence> evidence = new ArrayList<>(evidence(result.evidence(), scope));
        addTextEvidence(evidence, "INFO", "还缺少的数据", missing, scope);
        ChatView view = chatView(result, scope);
        return new AiManagerResponse("CHAT", "经营诊断", content, keyPoints, scope, evidence,
                actions(result.actions()), true, LocalDateTime.now(), null, view);
    }

    /** 把动态诊断仪表盘结果映射为响应，并构造兼容字段供历史记录与概览复用。 */
    static AiManagerResponse dashboard(AiDashboardResult result, Map<String, Object> scope) {
        if (result == null
                || (text(result.headline()).isBlank() && text(result.report()).isBlank())) {
            throw new BusinessException(502, "AI 模型本次未返回可展示的诊断内容，请稍后重新分析");
        }
        List<AiDashboardCard> cards = dashboardCards(result.cards(), scope);
        requireCards(cards, scope);
        AiDashboardResult dashboard = new AiDashboardResult(
                clip(text(result.headline()), MAX_HEADLINE), text(result.report()),
                score(result.score()), cards);
        // 由卡片反推兼容的证据与动作，让历史记录、概览计数和证据下钻继续可用。
        List<AiManagerResponse.Evidence> evidence = dashboardEvidence(cards, scope);
        List<AiManagerResponse.SuggestedAction> actions = dashboardActions(cards);
        return new AiManagerResponse("DIAGNOSIS_DASHBOARD", "AI 经营诊断", text(result.report()),
                List.of(), scope, evidence, actions, true, LocalDateTime.now(), dashboard, null);
    }

    /** 归一化诊断评分：总分裁剪到 0-100，等级按分值推导，维度分值裁剪并限量。无总分时返回 null。 */
    private static AiDashboardScore score(AiDashboardScore raw) {
        if (raw == null || raw.overall() == null) return null;
        int overall = clampScore(raw.overall());
        String grade = gradeOf(overall);
        String comment = clip(text(raw.comment()), 60);
        List<AiDashboardScoreDimension> dimensions = new ArrayList<>();
        if (raw.dimensions() != null) {
            for (AiDashboardScoreDimension item : raw.dimensions()) {
                if (dimensions.size() >= MAX_SCORE_DIMENSIONS || item == null) continue;
                String name = clip(text(item.name()), 10);
                if (name.isBlank() || item.score() == null) continue;
                dimensions.add(new AiDashboardScoreDimension(name, clampScore(item.score()),
                        clip(text(item.comment()), 40)));
            }
        }
        return new AiDashboardScore(overall, grade, comment, dimensions);
    }

    private static int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** 由总分推导等级，不采信模型自填的等级文案，保证与分值一致。 */
    private static String gradeOf(int score) {
        if (score >= 85) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 60) return "一般";
        return "较差";
    }

    private static AiManagerResponse response(String type, String title, String content,
                                              List<AiManagerResponse.SummaryPoint> keyPoints,
                                              Map<String, Object> scope,
                                              List<AiManagerResponse.Evidence> evidence,
                                              List<AiManagerResponse.SuggestedAction> actions) {
        return new AiManagerResponse(type, title, content, keyPoints, scope,
                evidence, actions, true, LocalDateTime.now(), null, null);
    }

    /** 只把已知组件类型和有内容的数据映射到前端，旧模型仍可只返回文字。 */
    private static ChatView chatView(ChatAnswerResult result, Map<String, Object> scope) {
        String tip = clip(text(result.tip()), 160);
        List<ChatCard> cards = chatCards(result.cards());
        List<String> followUps = followUps(result.followUps(), scope);
        if (tip.isBlank() && cards.isEmpty() && followUps.isEmpty()) return null;
        return new ChatView(tip, cards, followUps);
    }

    private static List<ChatCard> chatCards(List<ChatCard> items) {
        if (items == null || items.isEmpty()) return List.of();
        List<ChatCard> cards = new ArrayList<>();
        for (ChatCard item : items) {
            if (cards.size() >= MAX_CHAT_CARDS || item == null) continue;
            String type = text(item.type()).toUpperCase(Locale.ROOT);
            String title = clip(text(item.title()), MAX_CARD_TITLE);
            if (title.isBlank()) continue;
            String period = clip(text(item.period()), 40);
            AiMetricDefinition metric = AiMetricCatalog.find(text(item.metricCode()));
            String metricCode = metric == null ? null : metric.code();
            if ("CHART".equals(type)) {
                AiDashboardChart chart = chatChart(item.chart());
                if (chart != null && !period.isBlank())
                    cards.add(new ChatCard(type, title, period, metricCode, List.of(), chart));
            } else if ("RANKING".equals(type) || "INSIGHT_LIST".equals(type)) {
                List<ChatCardItem> cardItems = chatCardItems(item.items(), type);
                if (!cardItems.isEmpty() && (!"RANKING".equals(type) || !period.isBlank()))
                    cards.add(new ChatCard(type, title, period, metricCode, cardItems, null));
            }
        }
        return cards;
    }

    private static List<ChatCardItem> chatCardItems(List<ChatCardItem> items, String type) {
        if (items == null || items.isEmpty()) return List.of();
        List<ChatCardItem> result = new ArrayList<>();
        for (ChatCardItem item : items) {
            if (result.size() >= MAX_CHAT_ITEMS || item == null) continue;
            if ("RANKING".equals(type)) {
                String name = clip(text(item.name()), MAX_POINT_TITLE);
                String value = clip(text(item.value()), 32);
                if (name.isBlank() || !value.matches("-?(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d+)?")) continue;
                BigDecimal percent = item.percent() == null ? null
                        : item.percent().max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
                result.add(new ChatCardItem(name, value, clip(text(item.unit()), 12), percent,
                        null, null, null));
            } else {
                String title = clip(text(item.title()), MAX_POINT_TITLE);
                String detail = clip(text(item.detail()), 100);
                if (title.isBlank() || detail.isBlank()) continue;
                String icon = CHAT_ICONS.contains(text(item.icon())) ? text(item.icon()) : null;
                result.add(new ChatCardItem(null, null, null, null, icon, title, detail));
            }
        }
        return result;
    }

    private static AiDashboardChart chatChart(AiDashboardChart candidate) {
        if (candidate == null || candidate.categories() == null || candidate.categories().isEmpty()
                || candidate.categories().size() > MAX_CATEGORY || candidate.series() == null) return null;
        if (!Set.of("LINE", "BAR", "HBAR", "RING", "FUNNEL", "HEATMAP")
                .contains(text(candidate.chartType()).toUpperCase(Locale.ROOT))) return null;
        for (AiDashboardChartSeries series : candidate.series()) {
            if (series == null || series.data() == null
                    || series.data().size() != candidate.categories().size()
                    || series.data().stream().anyMatch(value -> value == null)) return null;
        }
        return chart(candidate);
    }

    private static List<String> followUps(List<String> items, Map<String, Object> scope) {
        if (items == null || items.isEmpty()) return List.of();
        boolean singleStore = "STORE".equals(String.valueOf(scope == null ? null : scope.get("scopeType")));
        Set<String> unique = new LinkedHashSet<>();
        for (String item : items) {
            if (unique.size() >= MAX_FOLLOW_UPS) break;
            String question = clip(text(item), 80);
            if (question.isBlank()) continue;
            if (singleStore && (question.contains("其他门店") || question.contains("各门店")
                    || question.contains("所有门店") || question.contains("跨门店"))) continue;
            unique.add(question);
        }
        return List.copyOf(unique);
    }

    /** 规整模型返回的卡片：裁剪数量、归一化枚举、回填指标元数据，并按顺序与优先级排序。 */
    private static List<AiDashboardCard> dashboardCards(List<AiDashboardCard> items,
                                                        Map<String, Object> scope) {
        if (items == null || items.isEmpty()) return List.of();
        String scopeType = scope == null ? "" : String.valueOf(scope.getOrDefault("scopeType", ""));
        List<AiDashboardCard> cards = new ArrayList<>();
        int index = 0;
        for (AiDashboardCard item : items) {
            if (cards.size() >= MAX_CARDS || item == null) continue;
            String title = clip(text(item.title()), MAX_CARD_TITLE);
            String type = cardType(item.type());
            AiDashboardChart chart = chart(item.chart());
            AiDashboardTable table = table(item.table());
            if ("CHART".equals(type) && chart == null) {
                // 图表数据缺失时退化为叙述卡，避免前端渲染空图。
                if (text(item.body()).isBlank()) continue;
                type = "NARRATIVE";
            }
            if ("TABLE".equals(type) && table == null) {
                // 表格数据缺失时退化为叙述卡，避免前端渲染空壳表格。
                if (text(item.body()).isBlank()) continue;
                type = "NARRATIVE";
            }
            if (title.isBlank() && !"CHART".equals(type)) continue;
            AiMetricDefinition metric = AiMetricCatalog.find(text(item.metricCode()));
            String metricCode = metric == null ? null : metric.code();
            String metricName = metric == null ? text(item.metricName()) : metric.name();
            String unit = text(item.unit()).isBlank() && metric != null ? metric.unit() : text(item.unit());
            String sourcePage = AiMetricCatalog.sourcePage(metric, scopeType);
            int order = item.order() == null ? index : item.order();
            cards.add(new AiDashboardCard(cardId(item.id(), index), type, level(item.level()), order,
                    cardSize(item.size()), title, text(item.body()), metricCode, metricName,
                    text(item.value()), unit, item.changeRate(), sourcePage,
                    evidenceRefs(item.evidenceRefs()), chart, table));
            index++;
        }
        cards.sort(Comparator
                .comparingInt((AiDashboardCard card) -> card.order() == null ? Integer.MAX_VALUE : card.order())
                .thenComparingInt(card -> levelPriority(card.level())));
        return cards;
    }

    private static AiDashboardChart chart(AiDashboardChart chart) {
        if (chart == null) return null;
        String type = chartType(chart.chartType());
        if (type == null) return null;
        List<AiDashboardChartSeries> series = new ArrayList<>();
        if (chart.series() != null) {
            for (AiDashboardChartSeries item : chart.series()) {
                if (series.size() >= MAX_SERIES || item == null
                        || item.data() == null || item.data().isEmpty()) {
                    continue;
                }
                List<BigDecimal> data = new ArrayList<>();
                // 缺失的数据点不能伪装为真实的零值；整张图退化为文字缺口。
                if (item.data().stream().anyMatch(value -> value == null)) return null;
                data.addAll(item.data());
                series.add(new AiDashboardChartSeries(clip(text(item.name()), MAX_POINT_TITLE), data));
            }
        }
        if (series.isEmpty()) return null;
        List<String> categories = new ArrayList<>();
        if (chart.categories() != null) {
            for (String category : chart.categories()) categories.add(clip(text(category), MAX_CATEGORY));
        }
        if (categories.isEmpty() || categories.size() > MAX_CATEGORY
                || series.stream().anyMatch(item -> item.data().size() != categories.size())) return null;
        return new AiDashboardChart(type, categories, series, text(chart.unit()));
    }

    /**
     * 规整表格：列数限 MAX_TABLE_COLUMNS，行数限 MAX_TABLE_ROWS，单元格文本裁剪，
     * 数值列校验为可解析数字（不合法则丢弃整表）。列行不足时返回 null，由调用方降级。
     */
    private static AiDashboardTable table(AiDashboardTable candidate) {
        if (candidate == null || candidate.columns() == null || candidate.columns().isEmpty()
                || candidate.rows() == null || candidate.rows().isEmpty()) {
            return null;
        }
        List<AiDashboardTableColumn> columns = new ArrayList<>();
        for (AiDashboardTableColumn column : candidate.columns()) {
            if (columns.size() >= MAX_TABLE_COLUMNS || column == null) continue;
            String label = clip(text(column.label()), MAX_TABLE_CELL);
            if (label.isBlank()) continue;
            columns.add(new AiDashboardTableColumn(text(column.key()), label,
                    align(column.align()), Boolean.TRUE.equals(column.numeric())));
        }
        if (columns.isEmpty()) return null;
        List<List<String>> rows = new ArrayList<>();
        for (List<String> row : candidate.rows()) {
            if (rows.size() >= MAX_TABLE_ROWS || row == null || row.isEmpty()) continue;
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                String cell = clip(text(i < row.size() ? row.get(i) : ""), MAX_TABLE_CELL);
                // 数值列单元格必须是可解析数字，否则丢弃整表，避免展示伪造或错位数据。
                if (columns.get(i).numeric() && !cell.isBlank() && !cell.matches(NUMERIC_CELL)) return null;
                cells.add(cell);
            }
            rows.add(cells);
        }
        if (rows.isEmpty()) return null;
        return new AiDashboardTable(columns, rows);
    }

    private static String align(String raw) {
        if (raw == null) return "left";
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "center", "right" -> raw.trim().toLowerCase(Locale.ROOT);
            default -> "left";
        };
    }

    private static List<AiManagerResponse.Evidence> dashboardEvidence(List<AiDashboardCard> cards,
                                                                      Map<String, Object> scope) {
        List<AiManagerResponse.Evidence> evidence = new ArrayList<>();
        for (AiDashboardCard card : cards) {
            if (evidence.size() >= MAX_ITEMS) break;
            // 叙述、图表与表格卡不作为可下钻证据。
            if ("NARRATIVE".equals(card.type()) || "CHART".equals(card.type())
                    || "TABLE".equals(card.type())) continue;
            if (text(card.title()).isBlank()) continue;
            evidence.add(new AiManagerResponse.Evidence(card.level(), card.title(), card.body(),
                    card.metricCode(), card.metricName(), card.value(), card.unit(), null, null,
                    null, card.changeRate(), null, sourceUpdatedAt(scope), card.sourcePage()));
        }
        return evidence;
    }

    private static List<AiManagerResponse.SuggestedAction> dashboardActions(List<AiDashboardCard> cards) {
        List<AiManagerResponse.SuggestedAction> actions = new ArrayList<>();
        for (AiDashboardCard card : cards) {
            if (actions.size() >= MAX_ITEMS) break;
            if (!"SUGGESTION".equals(card.type()) || text(card.title()).isBlank()) continue;
            actions.add(new AiManagerResponse.SuggestedAction(card.title(), card.body(), "", true));
        }
        return actions;
    }

    private static List<String> evidenceRefs(List<String> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String ref : refs) {
            if (result.size() >= MAX_ITEMS || text(ref).isBlank()) continue;
            result.add(text(ref));
        }
        return result;
    }

    private static String cardId(String raw, int index) {
        String id = text(raw);
        return id.isBlank() ? "card-" + index : id;
    }

    private static String cardType(String raw) {
        if (raw == null) return "METRIC";
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "METRIC", "RISK", "OPPORTUNITY", "SUGGESTION", "NARRATIVE", "CHART", "TABLE" ->
                    raw.trim().toUpperCase(Locale.ROOT);
            default -> "METRIC";
        };
    }

    private static String cardSize(String raw) {
        if (raw == null) return "MD";
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SM", "MD", "LG", "XL" -> raw.trim().toUpperCase(Locale.ROOT);
            default -> "MD";
        };
    }

    private static String chartType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "LINE", "BAR", "HBAR", "RING", "FUNNEL", "HEATMAP" -> raw.trim().toUpperCase(Locale.ROOT);
            default -> "BAR";
        };
    }

    private static int levelPriority(String level) {
        return switch (level) {
            case "WARNING" -> 0;
            case "OPPORTUNITY" -> 1;
            default -> 2;
        };
    }

    private static void requireCards(List<AiDashboardCard> cards, Map<String, Object> scope) {
        if (cards == null || cards.isEmpty()) {
            throw new BusinessException(502, "AI 模型本次未返回可展示的诊断卡片，请稍后重新分析");
        }
        if (Boolean.TRUE.equals(scope == null ? null : scope.get("dataAvailable"))
                && cards.stream().noneMatch(card -> ("METRIC".equals(card.type()) && !text(card.value()).isBlank())
                        || ("CHART".equals(card.type()) && card.chart() != null)
                        || ("TABLE".equals(card.type()) && card.table() != null))) {
            throw new BusinessException(502, "AI 本次未生成可核验的数据卡片，请重新诊断");
        }
    }

    private static List<AiManagerResponse.SummaryPoint> summaryPoints(List<AiTitledDetail> items) {
        if (items == null || items.isEmpty()) return List.of();
        List<AiManagerResponse.SummaryPoint> points = new ArrayList<>();
        for (AiTitledDetail item : items) {
            if (points.size() >= MAX_ITEMS || item == null || text(item.title()).isBlank()) continue;
            points.add(new AiManagerResponse.SummaryPoint(
                    clip(text(item.title()), MAX_POINT_TITLE), clip(text(item.detail()), MAX_POINT_DETAIL)));
        }
        return points;
    }

    private static List<AiManagerResponse.Evidence> evidence(List<AiEvidenceItem> items,
                                                             Map<String, Object> scope) {
        if (items == null || items.isEmpty()) return List.of();
        List<AiManagerResponse.Evidence> evidence = new ArrayList<>();
        for (AiEvidenceItem item : items) {
            if (evidence.size() >= MAX_ITEMS || item == null || text(item.title()).isBlank()) continue;
            evidence.add(evidenceItem(level(item.level()), text(item.title()), text(item.detail()),
                    item.metricCode(), scope));
        }
        return evidence;
    }

    private static void addDetails(List<AiManagerResponse.Evidence> target, String level,
                                   List<AiTitledDetail> items, Map<String, Object> scope) {
        if (items == null) return;
        for (AiTitledDetail item : items) {
            if (target.size() >= MAX_ITEMS || item == null || text(item.title()).isBlank()) continue;
            target.add(evidenceItem(level, text(item.title()), text(item.detail()), null, scope));
        }
    }

    private static void addTextEvidence(List<AiManagerResponse.Evidence> target, String level,
                                        String title, String detail, Map<String, Object> scope) {
        if (target.size() >= MAX_ITEMS || text(detail).isBlank()) return;
        target.add(evidenceItem(level, title, text(detail), null, scope));
    }

    /** 来源信息由服务端按实际查询上下文补充，模型只负责标题和说明。 */
    private static AiManagerResponse.Evidence evidenceItem(String level, String title, String detail,
                                                           String requestedMetricCode,
                                                           Map<String, Object> scope) {
        AiMetricDefinition metric = AiMetricCatalog.find(text(requestedMetricCode));
        String scopeType = scope == null ? "" : String.valueOf(scope.getOrDefault("scopeType", ""));
        String sourcePage = AiMetricCatalog.sourcePage(metric, scopeType);
        return new AiManagerResponse.Evidence(level, title, detail,
                metric == null ? null : metric.code(), metric == null ? title : metric.name(),
                null, metric == null ? null : metric.unit(), null, null, null, null, null,
                sourceUpdatedAt(scope), sourcePage);
    }

    private static LocalDateTime sourceUpdatedAt(Map<String, Object> scope) {
        if (scope == null) return null;
        Object value = scope.get("dataCutoffAt");
        if (value instanceof LocalDateTime time) return time;
        if (value == null) return null;
        try { return LocalDateTime.parse(String.valueOf(value)); }
        catch (RuntimeException ignored) { return null; }
    }

    private static List<AiManagerResponse.SuggestedAction> actions(List<AiActionItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        List<AiManagerResponse.SuggestedAction> actions = new ArrayList<>();
        for (AiActionItem item : items) {
            if (actions.size() >= MAX_ITEMS || item == null || text(item.title()).isBlank()) continue;
            // 模型不能把建议标成已确认，写操作仍须人工处理。
            actions.add(new AiManagerResponse.SuggestedAction(
                    text(item.title()), text(item.description()), text(item.risk()), true));
        }
        return actions;
    }

    private static void requireText(String value) {
        if (text(value).isBlank()) {
            throw new BusinessException(502, "AI 模型本次未返回可展示内容，请稍后重新分析");
        }
    }

    private static void requirePoints(List<AiManagerResponse.SummaryPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new BusinessException(502, "AI 模型本次未返回可展示的分点结论，请稍后重新分析");
        }
    }

    private static String level(String raw) {
        if (raw == null) return "INFO";
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "WARNING", "OPPORTUNITY", "INFO" -> raw.trim().toUpperCase(Locale.ROOT);
            default -> "INFO";
        };
    }

    private static String text(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= MAX_TEXT ? trimmed : trimmed.substring(0, MAX_TEXT);
    }

    /** 按字符数裁剪，超出时补省略号，用于保持要点精炼。 */
    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max).trim() + "…";
    }
}
