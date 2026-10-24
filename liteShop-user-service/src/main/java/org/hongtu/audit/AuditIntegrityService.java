package org.hongtu.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 验证审计日志的顺序、前驱摘要和内容 HMAC，并导出每日链头锚点。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditIntegrityService {
    private final JdbcTemplate jdbc;
    private final AuditSink sink;
    private final ObjectMapper mapper;

    @Value("${liteshop.audit.anchor-path:}")
    private String anchorPath;

    public Map<String, Object> verify(long tenantScopeId) {
        // 使用结果集逐条验证，避免完整性扫描时将整个租户的日志载入内存。
        Map<String, Object> scan = jdbc.query("SELECT * FROM sys_operation_audit "
                        + "WHERE tenant_scope_id=? ORDER BY chain_sequence",
                rs -> {
                    long sequence = 0;
                    String previous = "";
                    while (rs.next()) {
                        long current = rs.getLong("chain_sequence");
                        try {
                            if (current != sequence + 1 || !previous.equals(rs.getString("previous_hash"))
                                    || !sink.sign(previous + "\n" + rs.getString("canonical_payload"))
                                    .equals(rs.getString("entry_hash")))
                                return result(tenantScopeId, false, current, "序号、前驱摘要或内容校验失败");
                            if (!columnsMatch(rs))
                                return result(tenantScopeId, false, current, "落库字段与签名原文不一致");
                        } catch (Exception error) {
                            throw new IllegalStateException("审计日志完整性校验失败", error);
                        }
                        sequence = current;
                        previous = rs.getString("entry_hash");
                    }
                    return result(tenantScopeId, true, sequence, previous);
                }, tenantScopeId);
        if (scan == null || !Boolean.TRUE.equals(scan.get("valid"))) return scan;
        long sequence = (Long) scan.get("lastSequence");
        String previous = (String) scan.get("lastHash");
        List<Map<String, Object>> cursors = jdbc.query("SELECT last_sequence,last_hash "
                        + "FROM sys_operation_audit_chain WHERE tenant_scope_id=?",
                (rs, row) -> Map.of("sequence", rs.getLong(1), "hash", rs.getString(2)), tenantScopeId);
        if (sequence == 0 && cursors.isEmpty())
            return result(tenantScopeId, false, 0, "该范围暂无审计日志，无法校验完整性");
        if (cursors.size() != 1 || !Long.valueOf(sequence).equals(cursors.getFirst().get("sequence"))
                || !previous.equals(cursors.getFirst().get("hash")))
            return result(tenantScopeId, false, sequence, "链头游标与日志内容不一致");
        return result(tenantScopeId, true, sequence, previous);
    }

    /** 对照签名原文检查所有可查询字段，防止只改展示列却绕过 HMAC 校验。 */
    private boolean columnsMatch(ResultSet rs) throws Exception {
        JsonNode snapshot = mapper.readTree(rs.getString("canonical_payload"));
        JsonNode event = snapshot.path("event");
        String subjectTenant = value(event, "subjectTenantId");
        String scope = subjectTenant == null ? "0" : subjectTenant;
        String signedDiff = value(snapshot, "safeDiff");
        String storedDiff = rs.getString("diff_json");
        boolean diffMatches = signedDiff == null ? storedDiff == null
                : storedDiff != null && mapper.readTree(signedDiff).equals(mapper.readTree(storedDiff));
        return Objects.equals(rs.getString("id"), value(snapshot, "recordId"))
                && Objects.equals(rs.getString("tenant_scope_id"), scope)
                && Objects.equals(rs.getString("chain_sequence"), value(snapshot, "chainSequence"))
                && Objects.equals(rs.getString("key_version"), value(snapshot, "keyVersion"))
                && rs.getTimestamp("received_at").toInstant().equals(Instant.parse(value(snapshot, "receivedAt")))
                && Objects.equals(rs.getString("event_id"), value(event, "eventId"))
                && Objects.equals(rs.getString("actor_tenant_id"), value(event, "actorTenantId"))
                && Objects.equals(rs.getString("scope_tenant_id"), value(event, "scopeTenantId"))
                && Objects.equals(rs.getString("store_id"), value(event, "storeId"))
                && Objects.equals(rs.getString("actor_user_id"), value(event, "actorUserId"))
                && Objects.equals(rs.getString("actor_account"), value(snapshot, "actorAccount"))
                && rs.getTimestamp("occurred_at").toInstant().equals(
                Instant.parse(value(event, "occurredAt")).truncatedTo(ChronoUnit.MILLIS))
                && Objects.equals(rs.getString("client_ip"), value(event, "clientIp"))
                && Objects.equals(rs.getString("ip_location"), value(snapshot, "ipLocation"))
                && Objects.equals(rs.getString("geo_database_version"), value(snapshot, "geoDatabaseVersion"))
                && Objects.equals(rs.getString("service_name"), value(event, "serviceName"))
                && Objects.equals(rs.getString("module_code"), value(event, "moduleCode"))
                && Objects.equals(rs.getString("operation_type"), value(event, "operationType"))
                && Objects.equals(rs.getString("object_type"), value(event, "objectType"))
                && Objects.equals(rs.getString("object_id"), value(event, "objectId"))
                && Objects.equals(rs.getString("summary"), value(event, "summary"))
                && Objects.equals(rs.getString("result"), value(event, "result"))
                && Objects.equals(rs.getString("result_message"), value(event, "resultMessage"))
                && Objects.equals(rs.getString("request_id"), value(event, "requestId"))
                && Objects.equals(rs.getString("request_path"), value(event, "requestPath"))
                && Objects.equals(value(event, "diffJson"), signedDiff)
                && diffMatches;
    }

    private String value(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Map<String, Object> result(long scope, boolean valid, long sequence, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantScopeId", scope);
        result.put("valid", valid);
        result.put("lastSequence", sequence);
        result.put(valid ? "lastHash" : "reason", detail);
        return result;
    }

    /** 本地锚点文件须由部署侧同步到不可改写的独立存储。 */
    @Scheduled(cron = "${liteshop.audit.anchor-cron:0 5 0 * * *}")
    public void anchor() {
        if (anchorPath == null || anchorPath.isBlank()) return;
        List<Long> scopes = jdbc.query("SELECT tenant_scope_id FROM sys_operation_audit_chain",
                (rs, row) -> rs.getLong(1));
        try {
            Path path = Path.of(anchorPath);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            for (long scope : scopes) {
                Map<String, Object> verification = verify(scope);
                if (!Boolean.TRUE.equals(verification.get("valid"))) {
                    log.error("审计日志校验失败，租户范围={}", scope);
                    continue;
                }
                String statement = Instant.now() + " " + scope + " "
                        + verification.get("lastSequence") + " " + verification.get("lastHash");
                String line = statement + " " + sink.sign(statement) + System.lineSeparator();
                Files.writeString(path, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception error) {
            log.error("审计链头锚点写入失败", error);
        }
    }
}
