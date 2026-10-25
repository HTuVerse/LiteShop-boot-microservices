package org.hongtu.audit;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** 集中追加审计日志，并在每个租户范围内维护顺序校验链。 */
@Component
@RequiredArgsConstructor
public class AuditSink {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IdentifierGenerator ids;
    private final GeoCityLookup geo;

    @Value("${liteshop.audit.hmac-key:}")
    private String hmacKey;
    @Value("${liteshop.audit.key-version:v1}")
    private String keyVersion;

    @PostConstruct
    public void requireKey() {
        if (hmacKey == null || hmacKey.length() < 32)
            throw new IllegalStateException("审计 HMAC 密钥未配置或长度不足 32 字符");
    }

    /** 消息重复投递时通过 event_id 幂等，不会增加校验链序号。 */
    @RabbitListener(queues = {AuditMessaging.QUEUE, AuditMessaging.LEGACY_QUEUE})
    @Transactional
    public void receive(String serialized) throws Exception {
        AuditEvent event = mapper.readValue(serialized, AuditEvent.class);
        if (event.eventId() == null || event.eventId().isBlank()
                || event.moduleCode() == null || event.operationType() == null
                || event.result() == null || event.clientIp() == null)
            throw new IllegalArgumentException("审计事件缺少必填字段");
        Long existing = jdbc.query("SELECT id FROM sys_operation_audit WHERE event_id=? LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, event.eventId());
        if (existing != null) return;

        long scope = event.subjectTenantId() == null ? 0L : event.subjectTenantId();
        jdbc.update("INSERT IGNORE INTO sys_operation_audit_chain "
                + "(tenant_scope_id,last_sequence,last_hash) VALUES (?,0,'')", scope);
        Chain chain = jdbc.queryForObject("SELECT last_sequence,last_hash FROM sys_operation_audit_chain "
                + "WHERE tenant_scope_id=? FOR UPDATE",
                (rs, row) -> new Chain(rs.getLong(1), rs.getString(2)), scope);
        if (chain == null) throw new IllegalStateException("审计校验链初始化失败");
        // 相同事件可能由多个投递者并发重试，锁内再次判断以保持幂等。
        Long committed = jdbc.query("SELECT id FROM sys_operation_audit WHERE event_id=? LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, event.eventId());
        if (committed != null) return;

        GeoCityLookup.Location location = geo.lookup(event.clientIp());
        String actorAccount = actorAccount(event);
        String safeDiff = AuditSanitizer.safeDiff(mapper, event.diffJson());
        long sequence = chain.sequence() + 1;
        long recordId = ids.nextId(event).longValue();
        Instant receivedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuditEvent safeEvent = new AuditEvent(event.eventId(), event.occurredAt(),
                event.actorUserId(), event.actorAccount(), event.actorTenantId(),
                event.scopeTenantId(), event.subjectTenantId(), event.storeId(), event.clientIp(),
                event.serviceName(), event.moduleCode(), event.operationType(), event.objectType(),
                event.objectId(), event.summary(), event.result(), event.resultMessage(),
                event.requestId(), event.requestPath(), safeDiff);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recordId", String.valueOf(recordId));
        snapshot.put("tenantScopeId", String.valueOf(scope));
        snapshot.put("chainSequence", sequence);
        snapshot.put("keyVersion", keyVersion);
        snapshot.put("receivedAt", receivedAt.toString());
        snapshot.put("event", safeEvent);
        snapshot.put("actorAccount", actorAccount);
        snapshot.put("ipLocation", location.name());
        snapshot.put("geoDatabaseVersion", location.databaseVersion());
        snapshot.put("safeDiff", safeDiff);
        String canonical = mapper.writeValueAsString(snapshot);
        String previous = chain.hash();
        String digest = sign(previous + "\n" + canonical);

        jdbc.update("INSERT INTO sys_operation_audit (id,event_id,tenant_scope_id,actor_tenant_id,"
                        + "scope_tenant_id,store_id,actor_user_id,actor_account,occurred_at,received_at,client_ip,"
                        + "ip_location,geo_database_version,service_name,module_code,operation_type,"
                        + "object_type,object_id,summary,result,result_message,request_id,request_path,"
                        + "diff_json,canonical_payload,chain_sequence,previous_hash,entry_hash,key_version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                recordId, event.eventId(), scope, event.actorTenantId(),
                event.scopeTenantId(), event.storeId(), event.actorUserId(), actorAccount,
                Timestamp.from(Instant.parse(event.occurredAt())), Timestamp.from(receivedAt),
                event.clientIp(), location.name(),
                location.databaseVersion(), event.serviceName(), event.moduleCode(), event.operationType(),
                event.objectType(), event.objectId(), event.summary(), event.result(), event.resultMessage(),
                event.requestId(), event.requestPath(), safeDiff, canonical, sequence, previous, digest,
                keyVersion);
        jdbc.update("UPDATE sys_operation_audit_chain SET last_sequence=?,last_hash=? "
                + "WHERE tenant_scope_id=?", sequence, digest, scope);
    }

    /** 供定期完整性扫描复用，与写入时保持相同算法。 */
    public String sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String actorAccount(AuditEvent event) {
        String supplied = event.actorAccount();
        if (event.actorUserId() == null || !String.valueOf(event.actorUserId()).equals(supplied))
            return supplied;
        String current = jdbc.query("SELECT username FROM sys_user WHERE id=? LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, event.actorUserId());
        return current == null ? supplied : current;
    }

    private record Chain(long sequence, String hash) { }
}
