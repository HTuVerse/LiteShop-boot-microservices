package org.hongtu.audit;

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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 验证审计日志的顺序、前驱摘要和内容 HMAC，并导出每日链头锚点。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditIntegrityService {
    private final JdbcTemplate jdbc;
    private final AuditSink sink;

    @Value("${liteshop.audit.anchor-path:}")
    private String anchorPath;

    public Map<String, Object> verify(long tenantScopeId) {
        // 使用结果集逐条验证，避免完整性扫描时将整个租户的日志载入内存。
        Map<String, Object> scan = jdbc.query("SELECT chain_sequence,previous_hash,entry_hash,"
                        + "canonical_payload FROM sys_operation_audit "
                        + "WHERE tenant_scope_id=? ORDER BY chain_sequence",
                rs -> {
                    long sequence = 0;
                    String previous = "";
                    while (rs.next()) {
                        long current = rs.getLong(1);
                        try {
                            if (current != sequence + 1 || !previous.equals(rs.getString(2))
                                    || !sink.sign(previous + "\n" + rs.getString(4)).equals(rs.getString(3)))
                                return result(tenantScopeId, false, current, "序号、前驱摘要或内容校验失败");
                        } catch (Exception error) {
                            throw new IllegalStateException("审计日志完整性校验失败", error);
                        }
                        sequence = current;
                        previous = rs.getString(3);
                    }
                    return result(tenantScopeId, true, sequence, previous);
                }, tenantScopeId);
        if (scan == null || !Boolean.TRUE.equals(scan.get("valid"))) return scan;
        long sequence = (Long) scan.get("lastSequence");
        String previous = (String) scan.get("lastHash");
        List<Map<String, Object>> cursors = jdbc.query("SELECT last_sequence,last_hash "
                        + "FROM sys_operation_audit_chain WHERE tenant_scope_id=?",
                (rs, row) -> Map.of("sequence", rs.getLong(1), "hash", rs.getString(2)), tenantScopeId);
        if (cursors.size() != 1 || !Long.valueOf(sequence).equals(cursors.getFirst().get("sequence"))
                || !previous.equals(cursors.getFirst().get("hash")))
            return result(tenantScopeId, false, sequence, "链头游标与日志内容不一致");
        return result(tenantScopeId, true, sequence, previous);
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
