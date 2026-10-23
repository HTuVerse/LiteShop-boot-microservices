package org.hongtu.audit;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按平台、租户和权限范围提供只读审计查询。 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {
    private final JdbcTemplate jdbc;

    public Map<String, Object> page(AuditQuery query) {
        QuerySql sql = sql(query, false);
        int page = query.page() == null ? 1 : Math.max(1, query.page());
        int size = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + sql.fromWhere(), Long.class, sql.arguments().toArray());
        List<Object> args = new ArrayList<>(sql.arguments());
        args.add(size);
        args.add((page - 1L) * size);
        List<Map<String, Object>> records = jdbc.query("SELECT id,tenant_scope_id,actor_user_id,"
                        + "actor_account,occurred_at,client_ip,ip_location,module_code,operation_type,"
                        + "object_type,object_id,summary,result,service_name " + sql.fromWhere()
                        + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> row(rs, false), args.toArray());
        return Map.of("records", records, "total", total == null ? 0L : total,
                "current", page, "size", size);
    }

    public Map<String, Object> detail(long id) {
        AuditQuery empty = new AuditQuery(null, null, null, null, null, null, null, null,
                null, null);
        QuerySql sql = sql(empty, true);
        List<Object> args = new ArrayList<>(sql.arguments());
        args.add(id);
        List<Map<String, Object>> rows = jdbc.query("SELECT id,tenant_scope_id,actor_tenant_id,"
                        + "scope_tenant_id,store_id,actor_user_id,actor_account,occurred_at,received_at,"
                        + "client_ip,ip_location,geo_database_version,service_name,module_code,"
                        + "operation_type,object_type,object_id,summary,result,result_message,request_id,"
                        + "request_path,diff_json,chain_sequence,entry_hash,key_version "
                        + sql.fromWhere() + " AND id=? LIMIT 1",
                (rs, row) -> row(rs, true), args.toArray());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> export(AuditQuery query) {
        QuerySql sql = sql(query, false);
        List<Object> args = new ArrayList<>(sql.arguments());
        args.add(10001);
        List<Map<String, Object>> rows = jdbc.query("SELECT id,tenant_scope_id,actor_user_id,"
                        + "actor_account,occurred_at,client_ip,ip_location,module_code,operation_type,"
                        + "object_type,object_id,summary,result,service_name " + sql.fromWhere()
                        + " ORDER BY occurred_at DESC,id DESC LIMIT ?",
                (rs, row) -> row(rs, false), args.toArray());
        if (rows.size() > 10000) throw new IllegalArgumentException("导出结果超过 10000 条，请缩小筛选范围");
        return rows;
    }

    private QuerySql sql(AuditQuery query, boolean detail) {
        if (!TenantContext.isSuperAdmin() && !StpUtil.hasRole("ADMIN"))
            throw new SecurityException("只有平台或商户管理员可以查看审计日志");
        Long tenant = TenantContext.getTenantIdOrNull();
        if (!TenantContext.isSuperAdmin() && tenant == null)
            throw new SecurityException("缺少租户范围");
        StringBuilder where = new StringBuilder("FROM sys_operation_audit WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (tenant != null) {
            where.append(" AND tenant_scope_id=?");
            args.add(tenant);
        } else if (query.tenantId() != null) {
            where.append(" AND tenant_scope_id=?");
            args.add(query.tenantId());
        }
        if (!detail) {
            Instant end = query.endTime() == null ? Instant.now() : Instant.parse(query.endTime());
            Instant start = query.startTime() == null ? end.minus(7, ChronoUnit.DAYS)
                    : Instant.parse(query.startTime());
            if (start.isAfter(end) || start.isBefore(end.minus(31, ChronoUnit.DAYS)))
                throw new IllegalArgumentException("查询时间范围最多 31 天");
            where.append(" AND occurred_at>=? AND occurred_at<=?");
            args.add(Timestamp.from(start));
            args.add(Timestamp.from(end));
            appendLike(where, args, "actor_account", query.account());
            appendEqual(where, args, "client_ip", query.ip());
            appendEqual(where, args, "module_code", query.module());
            appendEqual(where, args, "operation_type", query.type());
            appendEqual(where, args, "result", query.result());
        }
        return new QuerySql(where.toString(), args);
    }

    private void appendEqual(StringBuilder where, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) return;
        where.append(" AND ").append(column).append("=?");
        args.add(value.trim());
    }

    private void appendLike(StringBuilder where, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) return;
        where.append(" AND ").append(column).append(" LIKE ? ESCAPE '\\\\'");
        args.add("%" + value.trim().replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%");
    }

    private Map<String, Object> row(ResultSet rs, boolean detail) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getString("id"));
        value.put("tenantId", rs.getLong("tenant_scope_id"));
        value.put("actorUserId", rs.getString("actor_user_id"));
        value.put("actorAccount", rs.getString("actor_account"));
        value.put("occurredAt", rs.getTimestamp("occurred_at").toInstant().toString());
        value.put("clientIp", rs.getString("client_ip"));
        value.put("ipLocation", rs.getString("ip_location"));
        value.put("moduleCode", rs.getString("module_code"));
        value.put("operationType", rs.getString("operation_type"));
        value.put("objectType", rs.getString("object_type"));
        value.put("objectId", rs.getString("object_id"));
        value.put("summary", rs.getString("summary"));
        value.put("result", rs.getString("result"));
        value.put("serviceName", rs.getString("service_name"));
        if (detail) {
            value.put("actorTenantId", rs.getString("actor_tenant_id"));
            value.put("scopeTenantId", rs.getString("scope_tenant_id"));
            value.put("storeId", rs.getString("store_id"));
            value.put("receivedAt", rs.getTimestamp("received_at").toInstant().toString());
            value.put("geoDatabaseVersion", rs.getString("geo_database_version"));
            value.put("resultMessage", rs.getString("result_message"));
            value.put("requestId", rs.getString("request_id"));
            value.put("requestPath", rs.getString("request_path"));
            value.put("diffJson", rs.getString("diff_json"));
            value.put("chainSequence", rs.getLong("chain_sequence"));
            value.put("entryHash", rs.getString("entry_hash"));
            value.put("keyVersion", rs.getString("key_version"));
        }
        return value;
    }

    private record QuerySql(String fromWhere, List<Object> arguments) { }

    /** 审计分页与导出使用同一组筛选条件。 */
    public record AuditQuery(Long tenantId, String account, String startTime, String endTime,
                             String ip, String module, String type, String result,
                             Integer page, Integer pageSize) { }
}
