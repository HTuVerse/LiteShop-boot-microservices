package org.hongtu.restaurant.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.VO.WsMessage;
import org.hongtu.restaurant.enums.SessionStatusEnum;
import org.hongtu.restaurant.enums.WsMessageTypeEnum;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.restaurant.security.TableSessionTokenService;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket 处理器
 *
 * @author Ht
 * @since 2025-11-05
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class NettyWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final AttributeKey<String> PENDING_SESSION = AttributeKey.valueOf("pendingSessionNo");

    @Resource
    private NettyChannelManager channelManager;

    @Resource
    private TableSessionService tableSessionService;

    @Resource
    private TableSessionTokenService tableSessionTokenService;

    @Resource
    private ClusterWebSocketBroadcaster clusterBroadcaster;

    private final ObjectMapper objectMapper;

    public NettyWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 握手完成
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake =
                    (WebSocketServerProtocolHandler.HandshakeComplete) evt;

            String uri = handshake.requestUri();
            // WebSocket 直连 Netty 端口，不经过 HTTP 租户拦截器，租户必须从签名票据中恢复。
            HandshakeParams params = extractHandshakeParams(uri);
            if (params == null) {
                QueryStringDecoder decoder = new QueryStringDecoder(uri);
                String sessionNo = firstQueryValue(decoder, "sessionNo");
                if (sessionNo == null || sessionNo.isBlank() || firstQueryValue(decoder, "token") != null) {
                    ctx.close();
                    return;
                }
                // 浏览器不能为 WebSocket 握手设置自定义认证头，改由首帧认证，避免票据进入 URL 日志。
                ctx.channel().attr(PENDING_SESSION).set(sessionNo);
                ctx.executor().schedule(() -> {
                    if (ctx.channel().isActive() && channelManager.getSessionKey(ctx.channel()) == null) ctx.close();
                }, 10, TimeUnit.SECONDS);
                return;
            }
            joinSession(ctx, params);
        }

        // 处理心跳超时
        else if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("读超时，关闭连接: channelId={}", ctx.channel().id().asShortText());
                ctx.close();
            } else if (event.state() == IdleState.WRITER_IDLE) {
                log.warn("写超时: channelId={}", ctx.channel().id().asShortText());
            } else if (event.state() == IdleState.ALL_IDLE) {
                log.warn("读写超时，关闭连接: channelId={}", ctx.channel().id().asShortText());
                ctx.close();
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    /** 完成票据验证后才将连接加入桌位广播组。 */
    private void joinSession(ChannelHandlerContext ctx, HandshakeParams params) {
            String sessionNo = params.sessionNo();
            TenantContext.setTenantId(params.tenantId());
            try {
                // 查询会自动追加 tenant_id，不匹配的 tenantId 与 sessionNo 无法建立连接。
                var tableSession = tableSessionService.getSessionDetailByNo(sessionNo);
                if (tableSession == null || tableSession.getStatus() == SessionStatusEnum.ENDED) {
                    log.warn("WebSocket 会话不存在或已结束: tenantId={}, sessionNo={}",
                            params.tenantId(), sessionNo);
                    ctx.close();
                    return;
                }

                channelManager.addChannel(params.tenantId(), sessionNo, ctx.channel());
                tableSessionService.increaseUserCount(sessionNo);

                Map<String, Object> joinData = new HashMap<>();
                joinData.put("currentUserCount",
                        tableSessionService.getSessionDetailByNo(sessionNo).getCurrentUserCount());
                joinData.put("sessionNo", sessionNo);
                WsMessage<Map<String, Object>> joinMessage = WsMessage.create(
                        WsMessageTypeEnum.USER_JOIN.getCode(), joinData, sessionNo, "新用户加入");
                clusterBroadcaster.broadcast(sessionNo, joinMessage);

                log.info("✅ WebSocket 连接建立: tenantId={}, sessionNo={}, channelId={}, 在线人数={}",
                        params.tenantId(), sessionNo, ctx.channel().id().asShortText(),
                        channelManager.getChannelCount(sessionNo));
            } finally {
                TenantContext.clear();
            }
    }

    /**
     * 接收消息
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        NettyChannelManager.SessionKey sessionKey = channelManager.getSessionKey(ctx.channel());
        String sessionNo = sessionKey == null ? null : sessionKey.sessionNo();
        String message = frame.text();

        log.info("收到消息: sessionNo={}, channelId={}, bytes={}",
                sessionNo, ctx.channel().id().asShortText(), message == null ? 0 : message.length());

        try {
            // 解析消息
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = objectMapper.readValue(message, Map.class);
            String type = (String) messageMap.get("type");

            if (sessionKey == null) {
                String pendingSessionNo = ctx.channel().attr(PENDING_SESSION).getAndSet(null);
                if (!"AUTH".equals(type) || pendingSessionNo == null) {
                    ctx.close();
                    return;
                }
                try {
                    TableSessionTokenService.Claims claims = tableSessionTokenService.verify((String) messageMap.get("token"));
                    if (!claims.sessionNo().equals(pendingSessionNo)) {
                        ctx.close();
                        return;
                    }
                    joinSession(ctx, new HandshakeParams(claims.tenantId(), pendingSessionNo));
                    if (ctx.channel().isActive() && channelManager.getSessionKey(ctx.channel()) != null) {
                        ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(
                                Map.of("type", "AUTH_OK", "sessionNo", pendingSessionNo))));
                    }
                } catch (RuntimeException e) {
                    ctx.close();
                }
                return;
            }

            // 心跳消息
            if (WsMessageTypeEnum.HEARTBEAT.getCode().equals(type)) {
                WsMessage<String> pong = WsMessage.create(
                        WsMessageTypeEnum.HEARTBEAT.getCode(),
                        "pong",
                        sessionNo
                );
                String pongJson = objectMapper.writeValueAsString(pong);
                ctx.writeAndFlush(new TextWebSocketFrame(pongJson));
                return;
            }

            // 其他消息类型可以在这里处理
            log.info("处理消息类型: type={}, sessionNo={}", type, sessionNo);

        } catch (Exception e) {
            log.error("处理消息失败: sessionNo={}, error={}", sessionNo, e.getMessage(), e);
        }
    }

    /**
     * 连接断开
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyChannelManager.SessionKey sessionKey = channelManager.getSessionKey(ctx.channel());
        String sessionNo = sessionKey == null ? null : sessionKey.sessionNo();

        if (sessionKey != null) {
            TenantContext.setTenantId(sessionKey.tenantId());
            try {
                channelManager.removeChannel(ctx.channel());
                tableSessionService.decreaseUserCount(sessionNo);

                Map<String, Object> leaveData = new HashMap<>();
                leaveData.put("currentUserCount",
                        tableSessionService.getSessionDetailByNo(sessionNo).getCurrentUserCount());
                leaveData.put("sessionNo", sessionNo);
                WsMessage<Map<String, Object>> leaveMessage = WsMessage.create(
                        WsMessageTypeEnum.USER_LEAVE.getCode(), leaveData, sessionNo, "用户离开");
                clusterBroadcaster.broadcast(sessionNo, leaveMessage);

                log.info("❌ WebSocket 连接断开: tenantId={}, sessionNo={}, channelId={}, 剩余在线人数={}",
                        sessionKey.tenantId(), sessionNo, ctx.channel().id().asShortText(),
                        channelManager.getChannelCount(sessionNo));
            } finally {
                TenantContext.clear();
            }
        }

        super.channelInactive(ctx);
    }

    /**
     * 异常处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        NettyChannelManager.SessionKey sessionKey = channelManager.getSessionKey(ctx.channel());
        String sessionNo = sessionKey == null ? null : sessionKey.sessionNo();
        log.error("⚠️ WebSocket 异常: sessionNo={}, channelId={}, error={}",
                sessionNo, ctx.channel().id().asShortText(), cause.getMessage(), cause);
        ctx.close();
    }

    /** URI: /ws/table-session?sessionNo={sessionNo}&token={signedToken} */
    private HandshakeParams extractHandshakeParams(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String sessionNo = firstQueryValue(decoder, "sessionNo");
        String token = firstQueryValue(decoder, "token");
        if (token == null || sessionNo == null || sessionNo.isBlank()) {
            return null;
        }
        try {
            TableSessionTokenService.Claims claims = tableSessionTokenService.verify(token);
            return claims.sessionNo().equals(sessionNo)
                    ? new HandshakeParams(claims.tenantId(), sessionNo)
                    : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String firstQueryValue(QueryStringDecoder decoder, String name) {
        var values = decoder.parameters().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private record HandshakeParams(Long tenantId, String sessionNo) {
    }
}

