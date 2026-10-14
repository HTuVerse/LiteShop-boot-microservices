package org.hongtu.restaurant.netty;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.VO.WsMessage;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Netty Channel 管理器
 * 管理所有 WebSocket 连接
 *
 * @author Ht
 * @since 2025-11-05
 */
@Slf4j
@Component
public class NettyChannelManager {

    /**
     * 会话编号 -> Channel 集合
     */
    private final Map<SessionKey, Set<Channel>> sessionChannels = new ConcurrentHashMap<>();

    /**
     * Channel ID -> 会话编号
     */
    private final Map<String, SessionKey> channelSessionMap = new ConcurrentHashMap<>();

    /**
     * JSON 序列化工具
     */
    private final ObjectMapper objectMapper;

    public NettyChannelManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 添加 Channel
     */
    public void addChannel(Long tenantId, String sessionNo, Channel channel) {
        SessionKey sessionKey = new SessionKey(tenantId, sessionNo);
        sessionChannels.computeIfAbsent(sessionKey, k -> new CopyOnWriteArraySet<>()).add(channel);
        channelSessionMap.put(channel.id().asShortText(), sessionKey);
        log.debug("添加 Channel: tenantId={}, sessionNo={}, channelId={}",
                tenantId, sessionNo, channel.id().asShortText());
    }

    /**
     * 移除 Channel
     */
    public void removeChannel(Channel channel) {
        SessionKey sessionKey = channelSessionMap.remove(channel.id().asShortText());
        if (sessionKey != null) {
            Set<Channel> channels = sessionChannels.get(sessionKey);
            if (channels != null) {
                channels.remove(channel);
                if (channels.isEmpty()) {
                    sessionChannels.remove(sessionKey);
                }
            }
            log.debug("移除 Channel: tenantId={}, sessionNo={}, channelId={}",
                    sessionKey.tenantId(), sessionKey.sessionNo(), channel.id().asShortText());
        }
    }

    /**
     * 获取会话编号
     */
    public SessionKey getSessionKey(Channel channel) {
        return channelSessionMap.get(channel.id().asShortText());
    }

    /**
     * 获取会话的 Channel 数量
     */
    public int getChannelCount(String sessionNo) {
        Set<Channel> channels = sessionChannels.get(currentKey(sessionNo));
        return channels == null ? 0 : channels.size();
    }

    /**
     * 向指定会话发送消息
     */
    public void sendToSession(String sessionNo, WsMessage<?> message) {
        Set<Channel> channels = sessionChannels.get(currentKey(sessionNo));
        if (channels == null || channels.isEmpty()) {
            log.warn("会话无在线用户: sessionNo={}", sessionNo);
            return;
        }

        try {
            String messageJson = objectMapper.writeValueAsString(message);
            int successCount = 0;
            int failCount = 0;

            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(messageJson));
                    successCount++;
                } else {
                    removeChannel(channel);
                    failCount++;
                }
            }

            log.info("💬 消息推送完成: sessionNo={}, type={}, 成功={}, 失败={}", 
                    sessionNo, message.getType(), successCount, failCount);

        } catch (Exception e) {
            log.error("消息序列化失败: sessionNo={}, error={}", sessionNo, e.getMessage(), e);
        }
    }

    /**
     * 向指定会话发送消息（排除指定 Channel）
     */
    public void sendToSessionExclude(String sessionNo, WsMessage<?> message, String excludeChannelId) {
        Set<Channel> channels = sessionChannels.get(currentKey(sessionNo));
        if (channels == null || channels.isEmpty()) {
            return;
        }

        try {
            String messageJson = objectMapper.writeValueAsString(message);
            for (Channel channel : channels) {
                if (!channel.id().asShortText().equals(excludeChannelId) && channel.isActive()) {
                    channel.writeAndFlush(new TextWebSocketFrame(messageJson));
                }
            }
        } catch (Exception e) {
            log.error("消息发送失败: sessionNo={}, error={}", sessionNo, e.getMessage(), e);
        }
    }

    /**
     * 获取所有会话编号
     */
    public Set<SessionKey> getAllSessionNos() {
        return sessionChannels.keySet();
    }

    /**
     * 检查会话是否有在线用户
     */
    public boolean hasOnlineUsers(String sessionNo) {
        Set<Channel> channels = sessionChannels.get(currentKey(sessionNo));
        return channels != null && !channels.isEmpty();
    }

    /** 会话结束后关闭本实例上的全部连接，其他实例由 Redis 广播各自执行。 */
    public void closeSession(String sessionNo) {
        SessionKey key = currentKey(sessionNo);
        Set<Channel> channels = sessionChannels.get(key);
        if (channels != null) {
            channels.forEach(Channel::close);
        }
    }

    private SessionKey currentKey(String sessionNo) {
        return new SessionKey(TenantContext.getTenantId(), sessionNo);
    }

    /** WebSocket 连接的完整隔离键，sessionNo 只要求在单个租户内唯一。 */
    public record SessionKey(Long tenantId, String sessionNo) {
        public SessionKey {
            if (tenantId == null || tenantId <= 0 || sessionNo == null || sessionNo.isBlank()) {
                throw new IllegalArgumentException("tenantId 和 sessionNo 不能为空");
            }
        }
    }
}

