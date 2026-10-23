package org.hongtu.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 将审计事件写入当前服务数据库，待确认投递后再从投递箱清理。 */
@Component
@RequiredArgsConstructor
public class AuditOutbox {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final ObjectMapper mapper;

    /** 在执行业务前确认审计投递箱可写，避免迁移遗漏后静默运行。 */
    public void requireReady() {
        jdbc().execute("SELECT 1 FROM sys_audit_outbox LIMIT 0");
    }

    public void append(AuditEvent event) {
        final String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("审计事件序列化失败", error);
        }
        jdbc().update("INSERT INTO sys_audit_outbox (event_id, payload) VALUES (?, ?)",
                event.eventId(), payload);
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate template = jdbcProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException("当前服务没有审计投递箱数据源");
        return template;
    }
}
