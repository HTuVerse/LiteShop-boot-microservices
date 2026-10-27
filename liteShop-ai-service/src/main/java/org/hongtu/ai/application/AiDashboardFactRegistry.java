package org.hongtu.ai.application;

import org.hongtu.ai.model.AiDashboardCard;
import org.hongtu.ai.model.AiDashboardResult;
import org.hongtu.ai.model.ChatAnswerResult;
import org.hongtu.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 保存单次问答或诊断中已授权只读工具的数值事实。 */
public final class AiDashboardFactRegistry {
    private static final Set<String> METADATA = Set.of("scopeType", "storeId", "storeName", "timeRange",
            "generatedAt", "dataCutoffAt", "dataPeriods", "metricCatalog", "sourceId");
    private final ObjectMapper mapper;
    private final Map<String, Source> sources = new ConcurrentHashMap<>();

    public AiDashboardFactRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String record(String toolName, String scopeType, Long storeId, String timeRange,
                         String json, LocalDateTime capturedAt) {
        if (json == null || json.isBlank() || "listAuthorizedStores".equals(toolName)) return null;
        try {
            Object value = mapper.readValue(json, Map.class);
            if (!(value instanceof Map<?, ?> data)) return null;
            String sourceId = UUID.randomUUID().toString();
            sources.put(sourceId, new Source(data, scopeType, storeId, timeRange, capturedAt));
            return sourceId;
        } catch (RuntimeException ignored) {
            // 失败、超限或非 JSON 的工具结果不能成为事实来源。
            return null;
        }
    }

    public Fact resolve(String sourceId, String jsonPointer) {
        Source source = sources.get(sourceId);
        if (source == null || jsonPointer == null || !jsonPointer.startsWith("/")) return null;
        Object current = source.data();
        for (String segment : jsonPointer.substring(1).split("/", -1)) {
            String key = segment.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(key)) return null;
                current = map.get(key);
            } else if (current instanceof List<?> items) {
                try {
                    int index = Integer.parseInt(key);
                    if (index < 0 || index >= items.size()) return null;
                    current = items.get(index);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            } else return null;
        }
        if (!(current instanceof Number number)) return null;
        return new Fact(new BigDecimal(number.toString()), source.scopeType(), source.storeId(),
                source.timeRange(), sourceId, jsonPointer, source.capturedAt());
    }

    public boolean hasNumericData() {
        return sources.values().stream().anyMatch(source -> source.data().entrySet().stream()
                .anyMatch(entry -> !METADATA.contains(String.valueOf(entry.getKey()))
                        && containsNumber(entry.getValue())));
    }

    public int recordedToolResults() {
        return sources.size();
    }

    /** 返回本轮实际工具数据的周期与刷新时间，避免使用请求默认周期冒充来源。 */
    public Map<String, Object> scopeMetadata() {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        String periods = sources.values().stream().map(source -> source.data().get("dataPeriods"))
                .filter(java.util.Objects::nonNull).map(String::valueOf).distinct()
                .collect(java.util.stream.Collectors.joining("；"));
        if (!periods.isBlank()) metadata.put("dataPeriods", periods);
        LocalDateTime oldestUpdate = sources.values().stream()
                .map(source -> source.data().get("dataCutoffAt"))
                .filter(java.util.Objects::nonNull)
                .map(value -> {
                    try { return LocalDateTime.parse(String.valueOf(value)); }
                    catch (RuntimeException ignored) { return null; }
                }).filter(java.util.Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
        if (oldestUpdate != null) metadata.put("dataCutoffAt", oldestUpdate);
        return metadata;
    }

    /** 核验可视化中的每个数字是否确实出现在本次授权工具结果中。 */
    public void validateDashboard(AiDashboardResult dashboard) {
        if (dashboard == null || dashboard.cards() == null) return;
        for (AiDashboardCard card : dashboard.cards()) {
            if (card == null) continue;
            if (card.value() != null && !card.value().isBlank()) requireRecorded(number(card.value()));
            if (card.changeRate() != null) requireRecorded(card.changeRate());
            if (card.chart() != null && card.chart().series() != null) {
                card.chart().series().stream().filter(java.util.Objects::nonNull)
                        .filter(series -> series.data() != null)
                        .flatMap(series -> series.data().stream()).filter(java.util.Objects::nonNull)
                        .forEach(this::requireRecorded);
            }
            if (card.table() != null && card.table().columns() != null && card.table().rows() != null) {
                for (List<String> row : card.table().rows()) {
                    if (row == null) continue;
                    for (int index = 0; index < Math.min(row.size(), card.table().columns().size()); index++) {
                        var column = card.table().columns().get(index);
                        if (column != null && Boolean.TRUE.equals(column.numeric())
                                && row.get(index) != null && !row.get(index).isBlank()) {
                            requireRecorded(number(row.get(index)));
                        }
                    }
                }
            }
        }
    }

    /** 问答中的排行和图表数值同样必须来自本轮工具结果。 */
    public void validateChat(ChatAnswerResult answer) {
        if (answer == null || answer.cards() == null) return;
        answer.cards().stream().filter(java.util.Objects::nonNull).forEach(card -> {
            if ("RANKING".equalsIgnoreCase(card.type()) && card.items() != null) {
                card.items().stream().filter(java.util.Objects::nonNull)
                        .filter(item -> item.value() != null && !item.value().isBlank())
                        .forEach(item -> requireRecorded(number(item.value())));
            }
            if (card.chart() != null && card.chart().series() != null) {
                card.chart().series().stream().filter(java.util.Objects::nonNull)
                        .filter(series -> series.data() != null)
                        .flatMap(series -> series.data().stream()).filter(java.util.Objects::nonNull)
                        .forEach(this::requireRecorded);
            }
        });
    }

    private BigDecimal number(String value) {
        try {
            return new BigDecimal(value.trim().replace(",", "").replace("%", ""));
        } catch (NumberFormatException error) {
            throw new BusinessException(502, "AI 诊断返回了无法核验的指标值，请重新诊断");
        }
    }

    private void requireRecorded(BigDecimal value) {
        if (sources.values().stream().noneMatch(source -> source.data().entrySet().stream()
                .anyMatch(entry -> !METADATA.contains(String.valueOf(entry.getKey()))
                        && containsValue(entry.getValue(), value)))) {
            throw new BusinessException(502, "AI 诊断数字与本次经营数据不一致，请重新诊断");
        }
    }

    private static boolean containsValue(Object candidate, BigDecimal value) {
        if (candidate instanceof Number number) {
            return new BigDecimal(number.toString()).compareTo(value) == 0;
        }
        if (candidate instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> !String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT)
                            .endsWith("id"))
                    .anyMatch(entry -> containsValue(entry.getValue(), value));
        }
        if (candidate instanceof List<?> list) {
            return list.stream().anyMatch(item -> containsValue(item, value));
        }
        return false;
    }

    private static boolean containsNumber(Object value) {
        if (value instanceof Number) return true;
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(AiDashboardFactRegistry::containsNumber);
        if (value instanceof List<?> list) return list.stream().anyMatch(AiDashboardFactRegistry::containsNumber);
        return false;
    }

    private record Source(Map<?, ?> data, String scopeType, Long storeId, String timeRange,
                          LocalDateTime capturedAt) {
    }

    public record Fact(BigDecimal value, String scopeType, Long storeId, String timeRange,
                       String sourceId, String jsonPointer, LocalDateTime capturedAt) {
    }
}
