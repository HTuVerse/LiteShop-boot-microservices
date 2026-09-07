package org.hongtu.restaurant.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.service.OrderTimeoutQueueService;
import org.hongtu.utils.RedisCache;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单超时队列服务实现
 * 使用 Redis ZSet 实现延迟队列
 * 
 * ZSet 结构：
 * - key: order:timeout:queue
 * - member: orderId（订单ID）
 * - score: 超时时间戳（秒）
 *
 * @author Ht
 * @since 2025-02-06
 */
@Slf4j
@Service
public class OrderTimeoutQueueServiceImpl implements OrderTimeoutQueueService {

    /**
     * Redis ZSet 的 key
     */
    private static final String TIMEOUT_QUEUE_KEY = "order:timeout:queue";

    @Resource
    private RedisCache redisCache;

    /**
     * 添加订单到超时队列（使用默认超时时间30分钟）
     */
    @Override
    public void addToTimeoutQueue(Long orderId) {
        addToTimeoutQueue(orderId, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 添加订单到超时队列（自定义超时时间）
     */
    @Override
    public void addToTimeoutQueue(Long orderId, int timeoutSeconds) {
        if (orderId == null) {
            log.warn("[订单超时队列] 订单ID为空，跳过添加");
            return;
        }

        try {
            // 计算超时时间戳（当前时间 + 超时秒数）
            double expireTimestamp = System.currentTimeMillis() / 1000.0 + timeoutSeconds;
            
            // 添加到 ZSet，score 为超时时间戳
            redisCache.redisTemplate.opsForZSet().add(TIMEOUT_QUEUE_KEY, orderId.toString(), expireTimestamp);
            
            log.info("[订单超时队列] 订单已加入超时队列: orderId={}, 超时时间={}秒后", orderId, timeoutSeconds);
        } catch (Exception e) {
            log.error("[订单超时队列] 添加订单到队列失败: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 从超时队列移除订单
     */
    @Override
    public void removeFromTimeoutQueue(Long orderId) {
        if (orderId == null) {
            return;
        }

        try {
            Long removed = redisCache.redisTemplate.opsForZSet().remove(TIMEOUT_QUEUE_KEY, orderId.toString());
            if (removed != null && removed > 0) {
                log.info("[订单超时队列] 订单已从超时队列移除: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("[订单超时队列] 移除订单失败: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 获取已超时的订单ID列表
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<Long> getTimeoutOrderIds() {
        try {
            // 当前时间戳（秒）
            double now = System.currentTimeMillis() / 1000.0;
            
            // 获取 score 小于当前时间的所有成员（即已超时的订单）
            Set<Object> timeoutMembers = redisCache.redisTemplate.opsForZSet()
                    .rangeByScore(TIMEOUT_QUEUE_KEY, 0, now);
            
            if (timeoutMembers == null || timeoutMembers.isEmpty()) {
                return Collections.emptySet();
            }
            
            // 转换为 Long 类型的订单ID集合
            return timeoutMembers.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(Collectors.toSet());
                    
        } catch (Exception e) {
            log.error("[订单超时队列] 获取超时订单列表失败: error={}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取队列中的订单数量
     */
    @Override
    public long getQueueSize() {
        try {
            Long size = redisCache.redisTemplate.opsForZSet().zCard(TIMEOUT_QUEUE_KEY);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("[订单超时队列] 获取队列大小失败: error={}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 检查订单是否在超时队列中
     */
    @Override
    public boolean isInQueue(Long orderId) {
        if (orderId == null) {
            return false;
        }

        try {
            Double score = redisCache.redisTemplate.opsForZSet().score(TIMEOUT_QUEUE_KEY, orderId.toString());
            return score != null;
        } catch (Exception e) {
            log.error("[订单超时队列] 检查订单是否在队列中失败: orderId={}, error={}", orderId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取订单的剩余超时时间（秒）
     */
    @Override
    public long getRemainingSeconds(Long orderId) {
        if (orderId == null) {
            return -1;
        }

        try {
            Double expireTimestamp = redisCache.redisTemplate.opsForZSet().score(TIMEOUT_QUEUE_KEY, orderId.toString());
            if (expireTimestamp == null) {
                return -1;
            }
            
            // 计算剩余时间
            long remaining = (long) (expireTimestamp - System.currentTimeMillis() / 1000.0);
            return Math.max(0, remaining);
        } catch (Exception e) {
            log.error("[订单超时队列] 获取剩余超时时间失败: orderId={}, error={}", orderId, e.getMessage(), e);
            return -1;
        }
    }
}
