package org.hongtu.restaurant.service;

import java.util.Set;

/**
 * 订单超时队列服务接口
 * 使用 Redis ZSet 实现延迟队列，用于订单超时自动取消
 *
 * @author Ht
 * @since 2025-02-06
 */
public interface OrderTimeoutQueueService {

    /**
     * 默认超时时间（秒）- 30分钟
     */
    int DEFAULT_TIMEOUT_SECONDS = 30 * 60;

    /**
     * 添加订单到超时队列
     * 订单创建时调用，设置超时时间为当前时间 + 30分钟
     *
     * @param orderId 订单ID
     */
    void addToTimeoutQueue(Long orderId);

    /**
     * 添加订单到超时队列（自定义超时时间）
     *
     * @param orderId        订单ID
     * @param timeoutSeconds 超时时间（秒）
     */
    void addToTimeoutQueue(Long orderId, int timeoutSeconds);

    /**
     * 从超时队列移除订单
     * 支付成功、手动取消时调用
     *
     * @param orderId 订单ID
     */
    void removeFromTimeoutQueue(Long orderId);

    /**
     * 获取已超时的订单ID列表
     * 获取所有超时时间戳小于当前时间的订单
     *
     * @return 超时订单ID集合
     */
    Set<Long> getTimeoutOrderIds();

    /**
     * 处理失败后放回超时队列，供下一轮原子领取。
     */
    void requeue(Long orderId);

    /**
     * 获取队列中的订单数量
     *
     * @return 队列长度
     */
    long getQueueSize();

    /**
     * 检查订单是否在超时队列中
     *
     * @param orderId 订单ID
     * @return 是否在队列中
     */
    boolean isInQueue(Long orderId);

    /**
     * 获取订单的剩余超时时间（秒）
     *
     * @param orderId 订单ID
     * @return 剩余秒数，如果不在队列中返回 -1
     */
    long getRemainingSeconds(Long orderId);
}
