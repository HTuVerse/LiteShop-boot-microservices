package org.hongtu.announcement.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 当前实例上的 SSE 连接。跨实例通知由 Redis 频道广播。 */
@Slf4j
@Component
public class AnnouncementSseRegistry {
    private final AtomicLong connectionIds = new AtomicLong();
    private final Map<Long, ClientConnection> clients = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId, Long tenantId, boolean storeRestricted, List<Long> storeIds) {
        SseEmitter emitter = new SseEmitter(0L);
        long connectionId = connectionIds.incrementAndGet();
        ClientConnection connection = new ClientConnection(
                connectionId, userId, tenantId, storeRestricted,
                storeIds == null ? List.of() : List.copyOf(storeIds), emitter);
        clients.put(connectionId, connection);

        Runnable cleanup = () -> clients.remove(connectionId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("connectionId", connectionId, "userId", userId)));
        } catch (IOException error) {
            clients.remove(connectionId);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    public void publish(AnnouncementRealtimeEvent event) {
        clients.values().stream()
                .filter(client -> matches(client, event))
                .forEach(client -> send(client, SseEmitter.event().name("announcement").data(event)));
    }

    @Scheduled(fixedDelayString = "${announcement.sse.heartbeat-ms:25000}")
    public void heartbeat() {
        clients.values().forEach(client -> send(client, SseEmitter.event().comment("heartbeat")));
    }

    int connectionCount() {
        return clients.size();
    }

    private boolean matches(ClientConnection client, AnnouncementRealtimeEvent event) {
        if (event.tenantId() == null) return true;
        if (!event.tenantId().equals(client.tenantId())) return false;
        if (event.storeIds().isEmpty() || !client.storeRestricted()) return true;
        return client.storeIds().stream().anyMatch(event.storeIds()::contains);
    }

    private void send(ClientConnection client, SseEmitter.SseEventBuilder event) {
        try {
            client.emitter().send(event);
        } catch (Exception error) {
            clients.remove(client.connectionId());
            log.debug("移除已断开的公告 SSE 连接, userId={}", client.userId());
        }
    }

    private record ClientConnection(long connectionId, Long userId, Long tenantId,
                                    boolean storeRestricted, List<Long> storeIds,
                                    SseEmitter emitter) {
    }
}
