package org.hongtu.announcement.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 发布数据库提交后的公告变更；Redis 不可用时至少通知当前实例。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementRealtimePublisher {
    public static final String CHANNEL = "liteshop:announcement:changed";

    private final StringRedisTemplate redisTemplate;
    private final AnnouncementSseRegistry registry;

    public void publish(AnnouncementRealtimeEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, AnnouncementEventCodec.encode(event));
        } catch (RuntimeException error) {
            log.warn("Redis 公告广播失败，降级为当前实例推送: {}", error.getMessage());
            registry.publish(event);
        }
    }
}
