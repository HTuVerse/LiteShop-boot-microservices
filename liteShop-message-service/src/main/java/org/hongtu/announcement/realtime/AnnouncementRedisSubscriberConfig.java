package org.hongtu.announcement.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class AnnouncementRedisSubscriberConfig {
    @Bean
    RedisMessageListenerContainer announcementRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            AnnouncementSseRegistry registry) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                registry.publish(AnnouncementEventCodec.decode(payload));
            } catch (RuntimeException error) {
                log.warn("忽略无效的公告 Redis 事件", error);
            }
        }, new ChannelTopic(AnnouncementRealtimePublisher.CHANNEL));
        return container;
    }
}
