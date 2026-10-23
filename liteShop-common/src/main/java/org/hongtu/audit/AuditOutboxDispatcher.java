package org.hongtu.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 重试投递审计事件，依赖消息代理的发布确认避免提前标记完成。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditOutboxDispatcher {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final RabbitTemplate rabbit;

    @Scheduled(fixedDelayString = "${liteshop.audit.dispatch-interval-ms:2000}")
    public void dispatch() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) return;
        List<Pending> pending;
        try {
            pending = jdbc.query("SELECT event_id, payload FROM sys_audit_outbox "
                            + "WHERE delivered_at IS NULL AND next_attempt_at <= NOW(3) "
                            + "ORDER BY created_at LIMIT 100",
                    (rs, rowNum) -> new Pending(rs.getString(1), rs.getString(2)));
        } catch (Exception error) {
            log.error("审计投递箱不可用，请先执行审计迁移脚本", error);
            return;
        }
        for (Pending row : pending) {
            try {
                rabbit.invoke(operations -> {
                    operations.convertAndSend(AuditMessaging.QUEUE, row.payload());
                    operations.waitForConfirmsOrDie(5000);
                    return null;
                });
                jdbc.update("UPDATE sys_audit_outbox SET delivered_at=NOW(3), attempts=attempts+1 "
                                + "WHERE event_id=? AND delivered_at IS NULL", row.eventId());
            } catch (Exception error) {
                jdbc.update("UPDATE sys_audit_outbox SET attempts=attempts+1, "
                                + "next_attempt_at=DATE_ADD(NOW(3), INTERVAL LEAST(300, POW(2, LEAST(attempts, 8))) SECOND) "
                                + "WHERE event_id=? AND delivered_at IS NULL", row.eventId());
                log.error("审计事件投递失败，eventId={}", row.eventId(), error);
            }
        }
        // 投递箱仅保留短期投递记录；正式日志由用户服务独立留存。
        jdbc.update("DELETE FROM sys_audit_outbox WHERE delivered_at < DATE_SUB(NOW(3), INTERVAL 7 DAY) LIMIT 100");
    }

    private record Pending(String eventId, String payload) { }
}
