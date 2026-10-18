package org.hongtu.api.util;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import org.hongtu.api.dto.ResourceBinding;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 逐页扫描本服务业务表，精确匹配历史 URL 并幂等回填文件库引用。 */
@RequiredArgsConstructor
public class ResourceBackfillEngine {
    private final JdbcTemplate jdbc;
    private final ResourceBindingCoordinator references;

    public enum Format { SINGLE, MULTI, SPEC_JSON }
    public record Field(String column, String name, Format format) { }
    public record Source(String table, String businessType, String activeCondition, List<Field> fields) { }

    /** 默认只预览；apply=true 时只添加精确匹配项，业务 URL 与外部 URL 均不改写。 */
    public Map<String, Object> run(Source source, long afterId, int limit, boolean apply) {
        if (afterId < 0 || limit < 1 || limit > 200 || !source.table().matches("[a-z_]+"))
            throw new IllegalArgumentException("回填分页参数不合法");
        String columns = String.join(",", source.fields().stream().map(Field::column).toList());
        String sql = "SELECT id,tenant_id," + columns + " FROM " + source.table()
                + " WHERE id > ? " + source.activeCondition() + " ORDER BY id LIMIT ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, afterId, limit);
        int matched = 0, unmatched = 0, applied = 0, skipped = 0;
        List<Map<String, Object>> unresolved = new ArrayList<>();
        long nextId = afterId;
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            long tenant = row.get("tenant_id") == null ? 0L : ((Number) row.get("tenant_id")).longValue();
            nextId = id;
            for (Field field : source.fields()) {
                String original = row.get(field.column()) == null ? null : row.get(field.column()).toString();
                if (original == null || original.isBlank()) continue;
                // 已有部分位置时保留原引用，只回填空缺位置；不能覆盖在线业务刚写入的关系。
                List<ResourceBinding> existing = references.references(tenant, source.businessType(), id).stream()
                        .filter(item -> field.name().equals(item.fieldName())).toList();
                Set<Integer> occupied = existing.stream().map(ResourceBinding::sortOrder).collect(Collectors.toSet());
                if (occupied.size() != existing.size()) { skipped++; continue; }
                List<String> urls = extract(original, field.format());
                List<ResourceBinding> slots = new ArrayList<>(existing);
                int fieldMatches = 0;
                for (int index = 0; index < urls.size(); index++) {
                    if (occupied.contains(index)) { skipped++; continue; }
                    String url = urls.get(index);
                    if (url == null || url.isBlank()) continue;
                    Long resourceId = references.matchExact(tenant, url);
                    if (resourceId == null) {
                        unmatched++;
                        if (unresolved.size() < 100)
                            unresolved.add(Map.of("businessType", source.businessType(), "businessId", id,
                                    "fieldName", field.name(), "sortOrder", index));
                    } else { matched++; fieldMatches++; }
                    if (resourceId != null) slots.add(new ResourceBinding(field.name(), index, resourceId, url));
                }
                if (!apply || fieldMatches == 0) continue;
                // 扫描期间被编辑的业务行不回填，避免旧快照覆盖新引用。
                String current = jdbc.queryForObject("SELECT " + field.column() + " FROM " + source.table()
                        + " WHERE id=?", String.class, id);
                if (!original.equals(current)) { skipped++; continue; }
                ResourceBindingPlan plan = references.prepare(references.request(tenant, source.businessType(),
                        id, field.name(), slots));
                references.completeAfterCommit(plan);
                applied += fieldMatches;
            }
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("scanned", rows.size()); summary.put("matched", matched);
        summary.put("unmatched", unmatched); summary.put("applied", applied);
        summary.put("skippedFields", skipped); summary.put("nextId", nextId);
        summary.put("unresolvedSample", unresolved);
        return summary;
    }

    private List<String> extract(String value, Format format) {
        if (format == Format.SINGLE) return List.of(value);
        if (format == Format.MULTI) return references.urlItems(value);
        try {
            List<String> urls = new ArrayList<>();
            for (Object item : JSON.parseArray(value))
                urls.add(item instanceof Map<?, ?> entry && entry.get("imageUrl") instanceof String url ? url : "");
            return urls;
        } catch (Exception error) {
            // 格式不确定的 JSON 不做推断，原业务数据保持不变。
            return List.of();
        }
    }
}
