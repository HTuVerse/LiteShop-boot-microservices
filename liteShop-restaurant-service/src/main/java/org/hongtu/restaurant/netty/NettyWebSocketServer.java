package org.hongtu.restaurant.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket 服务器
 *
 * @author Ht
 * @since 2025-11-05
 */
@Slf4j
@Component
public class NettyWebSocketServer {

    @Value("${netty.websocket.port:9090}")
    private int port;

    @Value("${netty.websocket.path:/ws/table-session}")
    private String path;

    @Resource
    private NettyWebSocketHandler nettyWebSocketHandler;

    private EventLoopGroup bossGroup;//负责接收客户端连接请求的线程组
    private EventLoopGroup workerGroup;//负责处理已建立连接的I/O操作的线程组

    /**
     * 启动 Netty WebSocket 服务器
     */
    @PostConstruct
    public void start() {
        new Thread(() -> {
            bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            //ChannelPipeline处理器链
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline()
                                        // HTTP 编解码器
                                        .addLast(new HttpServerCodec())
                                        // 分块写处理器
                                        .addLast(new ChunkedWriteHandler())
                                        // HTTP 消息聚合器，将多个消息聚合成单个完整的消息
                                        .addLast(new HttpObjectAggregator(65536))
                                        // 心跳检测：读超时60s，写超时60s，读写超时120s
                                        .addLast(new IdleStateHandler(300, 300, 600, TimeUnit.SECONDS))
                                        // WebSocket 协议处理器，指定路径
                                        // 路径格式: /ws/table-session?tenantId={tenantId}&sessionNo={sessionNo}
                                        .addLast(new WebSocketServerProtocolHandler(
                                                WebSocketServerProtocolConfig.newBuilder()
                                                        .websocketPath(path)
                                                        .allowExtensions(true)
                                                        .maxFramePayloadLength(65536)
                                                        .allowMaskMismatch(false)
                                                        .checkStartsWith(true)
                                                        .dropPongFrames(true)
                                                        .build()))
                                        // 自定义 WebSocket 处理器
                                        .addLast(nettyWebSocketHandler);
                            }
                        });

                ChannelFuture future = bootstrap.bind(port).sync();
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("❌ Netty WebSocket 服务器启动失败: {}", e.getMessage(), e);
            } finally {
                shutdown();
            }
        }, "NettyWebSocketServer").start();
    }

    /**
     * 关闭服务器
     */
    @PreDestroy
    public void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty WebSocket 服务器已关闭");
    }
}

