package org.hongtu.restaurant.config;

import org.hongtu.restaurant.netty.ClusterWebSocketBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** WebSocket 跨实例 Redis 订阅配置。 */
@Configuration
public class WebSocketRedisConfig {

    @Bean
    public RedisMessageListenerContainer webSocketRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ClusterWebSocketBroadcaster broadcaster) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(broadcaster, new ChannelTopic(ClusterWebSocketBroadcaster.CHANNEL));
        return container;
    }
}
