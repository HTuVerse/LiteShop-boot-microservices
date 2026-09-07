package org.hongtu.restaurant.netty;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.restaurant.VO.TableSessionVO;
import org.hongtu.restaurant.VO.WsMessage;
import org.hongtu.restaurant.VO.cart.CartItemVO;
import org.hongtu.restaurant.enums.WsMessageTypeEnum;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Netty WebSocket 推送服务
 *
 * @author Ht
 * @since 2025-11-05
 */
@Slf4j
@Service
public class NettyWebSocketPushService {

    @Resource
    private NettyChannelManager channelManager;

    /**
     * 推送新订单创建消息
     */
    public void pushOrderCreated(String sessionNo, RestaurantOrder order, Long userId, String userName) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("totalAmount", order.getTotalAmount());
        data.put("actualAmount", order.getActualAmount());
        data.put("diningCount", order.getDiningCount());
        data.put("note", order.getNote());
        data.put("createdAt", order.getCreatedAt());

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.ORDER_CREATED.getCode(),
                data,
                sessionNo,
                userId,
                userName
        );
        message.setMsg(userName + " 创建了新订单");

        channelManager.sendToSession(sessionNo, message);
        log.info("推送订单创建消息: sessionNo={}, orderId={}", sessionNo, order.getId());
    }

    /**
     * 推送订单创建消息（带完整订单VO）
     */
    public void pushOrderCreated(String sessionNo, RestaurantOrderVO orderVO) {
        WsMessage<RestaurantOrderVO> message = WsMessage.create(
                WsMessageTypeEnum.ORDER_CREATED.getCode(),
                orderVO,
                sessionNo,
                "新订单创建"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送订单创建消息: sessionNo={}, orderId={}", sessionNo, orderVO.getId());
    }

    /**
     * 推送订单更新消息
     */
    public void pushOrderUpdated(String sessionNo, RestaurantOrderVO orderVO) {
        WsMessage<RestaurantOrderVO> message = WsMessage.create(
                WsMessageTypeEnum.ORDER_UPDATED.getCode(),
                orderVO,
                sessionNo,
                "订单已更新"
        );

        channelManager.sendToSession(sessionNo, message);
    }

    /**
     * 推送订单支付信息
     */
    public void pushOrderPaid(String sessionNo, RestaurantOrderVO orderVO) {
        WsMessage<RestaurantOrderVO> message = WsMessage.create(
                WsMessageTypeEnum.ORDER_PAID.getCode(),
                orderVO,
                sessionNo,
                "订单已支付"
        );
        channelManager.sendToSession(sessionNo, message);
    }

    /**
     * 推送订单支付成功通知（带取餐码）
     */
    public void sendOrderPaidNotification(String sessionNo, Long orderId, String orderNo, String pickupCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("orderNo", orderNo);
        data.put("pickupCode", pickupCode);
        data.put("message", pickupCode != null ? "订单已支付，取餐码：" + pickupCode : "订单已支付");

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.ORDER_PAID.getCode(),
                data,
                sessionNo,
                "订单支付成功"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送订单支付成功通知: sessionNo={}, orderId={}, pickupCode={}", sessionNo, orderId, pickupCode);
    }

    /**
     * 推送订单取消消息
     */
    public void pushOrderCancelled(String sessionNo, Long orderId, String orderNo, String cancelReason) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("orderNo", orderNo);
        data.put("cancelReason", cancelReason);

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.ORDER_CANCELLED.getCode(),
                data,
                sessionNo,
                "订单已取消"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送订单取消消息: sessionNo={}, orderId={}", sessionNo, orderId);
    }

    /**
     * 推送会话统计更新消息
     */
    public void pushSessionStatsUpdated(String sessionNo, TableSessionVO sessionVO) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", sessionVO.getTotalOrders());
        data.put("totalAmount", sessionVO.getTotalAmount());
        data.put("currentUserCount", sessionVO.getCurrentUserCount());

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.SESSION_STATS_UPDATED.getCode(),
                data,
                sessionNo,
                "会话统计已更新"
        );

        channelManager.sendToSession(sessionNo, message);
    }

    /**
     * 推送会话结束消息
     */
    public void pushSessionEnded(String sessionNo, BigDecimal paidAmount) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionNo", sessionNo);
        data.put("paidAmount", paidAmount);
        data.put("message", "本桌已结账，感谢光临！");

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.SESSION_ENDED.getCode(),
                data,
                sessionNo,
                "会话已结束"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送会话结束消息: sessionNo={}, paidAmount={}", sessionNo, paidAmount);
    }

    /**
     * 推送系统通知
     */
    public void pushSystemNotify(String sessionNo, String title, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.SYSTEM_NOTIFY.getCode(),
                data,
                sessionNo,
                title
        );

        channelManager.sendToSession(sessionNo, message);
    }

    /**
     * 推送用户加入消息
     */
    public void pushUserJoin(String sessionNo, String userName) {
        Map<String, Object> data = new HashMap<>();
        data.put("userName", userName);
        data.put("message", userName + " 加入了餐桌");

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.USER_JOIN.getCode(),
                data,
                sessionNo,
                userName + " 加入了餐桌"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送用户加入消息: sessionNo={}, userName={}", sessionNo, userName);
    }

    /**
     * 推送用户离开消息
     */
    public void pushUserLeave(String sessionNo, String userName) {
        Map<String, Object> data = new HashMap<>();
        data.put("userName", userName);
        data.put("message", userName + " 离开了餐桌");

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.USER_LEAVE.getCode(),
                data,
                sessionNo,
                userName + " 离开了餐桌"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送用户离开消息: sessionNo={}, userName={}", sessionNo, userName);
    }

    /**
     * 检查会话是否有在线用户
     */
    public boolean hasOnlineUsers(String sessionNo) {
        return channelManager.hasOnlineUsers(sessionNo);
    }

    /**
     * 获取会话在线人数
     */
    public int getOnlineCount(String sessionNo) {
        return channelManager.getChannelCount(sessionNo);
    }

    /**
     * 推送购物车商品添加消息
     */
    public void pushCartItemAdded(String sessionNo, CartItemVO cartItem) {
        WsMessage<CartItemVO> message = WsMessage.create(
                WsMessageTypeEnum.CART_ITEM_ADDED.getCode(),
                cartItem,
                sessionNo,
                cartItem.getAddedByUserId(),
                cartItem.getAddedByName(),
                cartItem.getAddedByName() + " 添加了商品"

        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送购物车商品添加: sessionNo={}, dishName={}", sessionNo, cartItem.getDishName());
    }

    /**
     * 推送购物车商品更新消息
     */
    public void pushCartItemUpdated(String sessionNo, CartItemVO cartItem) {
        WsMessage<CartItemVO> message = WsMessage.create(
                WsMessageTypeEnum.CART_ITEM_UPDATED.getCode(),
                cartItem,
                sessionNo,
                "购物车商品已更新"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送购物车商品更新: sessionNo={}, cartItemId={}", sessionNo, cartItem.getCartItemId());
    }

    /**
     * 推送购物车商品移除消息
     */
    public void pushCartItemRemoved(String sessionNo, String cartItemId) {
        Map<String, Object> data = new HashMap<>();
        data.put("cartItemId", cartItemId);
        data.put("sessionNo", sessionNo);

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.CART_ITEM_REMOVED.getCode(),
                data,
                sessionNo,
                "购物车商品已移除"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送购物车商品移除: sessionNo={}, cartItemId={}", sessionNo, cartItemId);
    }

    /**
     * 推送购物车清空消息
     */
    public void pushCartCleared(String sessionNo) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionNo", sessionNo);
        data.put("message", "购物车已清空");

        WsMessage<Map<String, Object>> message = WsMessage.create(
                WsMessageTypeEnum.CART_CLEARED.getCode(),
                data,
                sessionNo,
                "购物车已清空"
        );

        channelManager.sendToSession(sessionNo, message);
        log.info("推送购物车清空: sessionNo={}", sessionNo);
    }

    /**
     * 推送就餐人数更新消息
     */
    public void pushSessionUpdated(String sessionNo, TableSessionVO updatedSession) {
        WsMessage<TableSessionVO> message = WsMessage.create(
                WsMessageTypeEnum.SESSION_DINING_COUNT_UPDATE.getCode(),
                updatedSession,
                sessionNo,
                "就餐人数已更新"
        );

        channelManager.sendToSession(sessionNo, message);
    }
}

