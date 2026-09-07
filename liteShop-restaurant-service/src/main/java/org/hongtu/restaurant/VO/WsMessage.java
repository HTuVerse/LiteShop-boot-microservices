package org.hongtu.restaurant.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * WebSocket 消息对象
 *
 * @author Ht
 * @since 2025-11-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    private String type;

    /**
     * 消息内容
     */
    private T data;

    /**
     * 消息时间
     */
    private LocalDateTime timestamp;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 发送者ID（可选）
     */
    private Long senderId;

    /**
     * 发送者昵称（可选）
     */
    private String senderName;

    /**
     * 消息描述
     */
    private String msg;

    /**
     * 创建消息
     */
    public static <T> WsMessage<T> create(String type, T data, String sessionNo) {
        return WsMessage.<T>builder()
                .type(type)
                .data(data)
                .sessionNo(sessionNo)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建消息（带发送者信息）
     */
    public static <T> WsMessage<T> create(String type, T data, String sessionNo, Long senderId, String senderName) {
        return WsMessage.<T>builder()
                .type(type)
                .data(data)
                .sessionNo(sessionNo)
                .senderId(senderId)
                .senderName(senderName)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建消息（带描述）
     */
    public static <T> WsMessage<T> create(String type, T data, String sessionNo, String message) {
        return WsMessage.<T>builder()
                .type(type)
                .data(data)
                .sessionNo(sessionNo)
                .msg(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建消息（带发送者信息和描述）
     */
    public static <T> WsMessage<T> create(String type, T data, String sessionNo, Long senderId, String senderName,String message) {
        return WsMessage.<T>builder()
                .type(type)
                .data(data)
                .sessionNo(sessionNo)
                .senderId(senderId)
                .senderName(senderName)
                .msg(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

