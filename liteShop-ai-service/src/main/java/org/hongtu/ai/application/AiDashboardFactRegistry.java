package org.hongtu.ai.application;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 保存单次诊断已授权只读工具的数值事实。 */
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
