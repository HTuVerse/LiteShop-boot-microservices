package org.hongtu.restaurant.netty;

import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.VO.WsMessage;
import org.hongtu.restaurant.enums.WsMessageTypeEnum;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * WebSocket 跨实例广播。业务实例只向 Redis 发布一次，每个节点再投递到自己的本地连接。
 */
@Slf4j
@Component
public class ClusterWebSocketBroadcaster implements MessageListener {

    public static final String CHANNEL = "restaurant:websocket:broadcast";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final NettyChannelManager channelManager;

    public ClusterWebSocketBroadcaster(StringRedisTemplate stringRedisTemplate,
                                       ObjectMapper objectMapper,
                                       NettyChannelManager channelManager) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.channelManager = channelManager;
    }

    public void broadcast(String sessionNo, WsMessage<?> message) {
        Long tenantId = TenantContext.getTenantId();
        try {
            BroadcastEnvelope envelope = new BroadcastEnvelope(
                    tenantId, sessionNo, objectMapper.writeValueAsString(message));
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            throw new IllegalStateException("WebSocket 跨实例消息发布失败", e);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            BroadcastEnvelope envelope = objectMapper.readValue(body, BroadcastEnvelope.class);
            if (envelope.tenantId() == null || envelope.sessionNo() == null || envelope.payload() == null) {
                throw new IllegalArgumentException("WebSocket 广播消息缺少必要字段");
            }
            TenantScope.run(envelope.tenantId(), () -> {
                try {
                    WsMessage<?> wsMessage = objectMapper.readValue(envelope.payload(), WsMessage.class);
                    channelManager.sendToSession(envelope.sessionNo(), wsMessage);
                    if (WsMessageTypeEnum.SESSION_ENDED.getCode().equals(wsMessage.getType())) {
                        channelManager.closeSession(envelope.sessionNo());
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (Exception e) {
            log.error("消费 WebSocket 跨实例广播失败", e);
        }
    }

    private record BroadcastEnvelope(Long tenantId, String sessionNo, String payload) {
    }
}
