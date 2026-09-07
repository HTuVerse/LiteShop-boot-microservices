package org.hongtu.restaurant.enums;

import lombok.Getter;

/**
 * WebSocket 消息类型枚举
 *
 * @author Ht
 * @since 2025-11-05
 */
@Getter
public enum WsMessageTypeEnum {

    /**
     * 用户加入
     */
    USER_JOIN("USER_JOIN", "用户加入"),

    /**
     * 用户离开
     */
    USER_LEAVE("USER_LEAVE", "用户离开"),

    /**
     * 新订单创建
     */
    ORDER_CREATED("ORDER_CREATED", "新订单创建"),

    /**
     * 订单更新
     */
    ORDER_UPDATED("ORDER_UPDATED", "订单更新"),

    /**
     * 订单支付
     */
    ORDER_PAID("ORDER_PAID", "订单支付"),

    /**
     * 订单取消
     */
    ORDER_CANCELLED("ORDER_CANCELLED", "订单取消"),

    /**
     * 会话统计更新
     */
    SESSION_STATS_UPDATED("SESSION_STATS_UPDATED", "会话统计更新"),

    /**
     * 会话结束
     */
    SESSION_ENDED("SESSION_ENDED", "会话结束"),

    /**
     * 就餐人数更新
     * */
    SESSION_DINING_COUNT_UPDATE("SESSION_DINING_COUNT_UPDATE","就餐人数更新"),

    /**
     * 心跳
     */
    HEARTBEAT("HEARTBEAT", "心跳"),

    /**
     * 系统通知
     */
    SYSTEM_NOTIFY("SYSTEM_NOTIFY", "系统通知"),

    /**
     * 购物车商品添加
     */
    CART_ITEM_ADDED("CART_ITEM_ADDED", "购物车商品添加"),

    /**
     * 购物车商品更新
     */
    CART_ITEM_UPDATED("CART_ITEM_UPDATED", "购物车商品更新"),

    /**
     * 购物车商品移除
     */
    CART_ITEM_REMOVED("CART_ITEM_REMOVED", "购物车商品移除"),

    /**
     * 购物车清空
     */
    CART_CLEARED("CART_CLEARED", "购物车清空");

    private final String code;
    private final String desc;

    WsMessageTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

