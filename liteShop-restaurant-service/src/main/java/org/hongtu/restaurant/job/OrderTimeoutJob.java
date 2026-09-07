package org.hongtu.restaurant.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.service.OrderTimeoutQueueService;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 订单超时自动取消定时任务
 * 使用 Quartz 框架，每5秒扫描一次 Redis 延迟队列
 * 处理所有已超时未支付的订单
 *
 * @author Ht
 * @since 2025-02-06
 */
@Slf4j
@Component
public class OrderTimeoutJob implements Job {

    @Resource
    private OrderTimeoutQueueService orderTimeoutQueueService;

    @Resource
    private RestaurantOrderService restaurantOrderService;

    /**
     * 执行定时任务
     * 1. 从 Redis 延迟队列获取已超时的订单
     * 2. 验证订单状态（防止重复处理）
     * 3. 调用超时处理逻辑取消订单
     * 4. 从队列中移除已处理的订单
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // 1. 获取所有已超时的订单ID
            Set<Long> timeoutOrderIds = orderTimeoutQueueService.getTimeoutOrderIds();
            
            if (timeoutOrderIds.isEmpty()) {
                return; // 没有超时订单，直接返回
            }

            log.info("[订单超时任务] 发现 {} 个超时订单，开始处理...", timeoutOrderIds.size());

            int successCount = 0;
            int skipCount = 0;
            int failCount = 0;

            // 2. 遍历处理每个超时订单
            for (Long orderId : timeoutOrderIds) {
                try {
                    // 2.1 查询订单当前状态
                    RestaurantOrder order = TenantIgnore.get(() -> restaurantOrderService.getById(orderId));
                    
                    if (order == null) {
                        // 订单不存在，从队列移除
                        orderTimeoutQueueService.removeFromTimeoutQueue(orderId);
                        log.warn("[订单超时任务] 订单不存在，已从队列移除: orderId={}", orderId);
                        skipCount++;
                        continue;
                    }

                    if (order.getTenantId() == null) {
                        log.error("[订单超时任务] 订单缺少tenant_id，拒绝处理: orderId={}", orderId);
                        failCount++;
                        continue;
                    }
                    TenantContext.setTenantId(order.getTenantId());

                    try {

                    // 2.2 检查订单是否需要处理
                    if (!needProcess(order)) {
                        // 订单已支付/已取消/已完成，从队列移除
                        orderTimeoutQueueService.removeFromTimeoutQueue(orderId);
                        log.info("[订单超时任务] 订单无需处理（状态: {}, 支付状态: {}），已从队列移除: orderId={}", 
                                order.getStatus(), order.getPaymentStatus(), orderId);
                        skipCount++;
                        continue;
                    }

                    // 2.3 执行超时取消
                    boolean result = restaurantOrderService.handlePaymentTimeout(
                            orderId, 
                            "订单超时自动取消（超过30分钟未支付）"
                    );

                    if (result) {
                        // 仅在处理成功后移除；瞬时异常或失败保留在队列中，供下次调度重试。
                        orderTimeoutQueueService.removeFromTimeoutQueue(orderId);
                        successCount++;
                        log.info("[订单超时任务] 订单超时取消成功: orderId={}, orderNo={}", 
                                orderId, order.getOrderNo());
                    } else {
                        failCount++;
                        log.warn("[订单超时任务] 订单超时取消失败，将在下次调度重试: orderId={}, orderNo={}",
                                orderId, order.getOrderNo());
                    }
                    } finally {
                        TenantContext.clear();
                    }

                } catch (Exception e) {
                    TenantContext.clear();
                    failCount++;
                    // 单个订单处理失败不影响其他订单
                    log.error("[订单超时任务] 处理订单异常: orderId={}, error={}", orderId, e.getMessage(), e);
                    // 保留队列成员，瞬时故障恢复后由下次调度继续处理。
                }
            }

            log.info("[订单超时任务] 处理完成，成功: {}，跳过: {}，失败: {}", 
                    successCount, skipCount, failCount);

        } catch (Exception e) {
            log.error("[订单超时任务] 执行异常: {}", e.getMessage(), e);
            // 不抛出异常，避免影响下次调度
        }
    }

    /**
     * 判断订单是否需要处理
     * 只有未支付且状态为待支付/已下单的订单才需要超时取消
     */
    private boolean needProcess(RestaurantOrder order) {
        // 已支付的订单不处理
        if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            return false;
        }
        
        // 已取消的订单不处理
        if (order.getStatus() == OrderStatusEnum.CANCELLED) {
            return false;
        }
        
        // 已完成的订单不处理
        if (order.getStatus() == OrderStatusEnum.COMPLETED) {
            return false;
        }
        
        // 只处理未支付状态的订单
        return order.getPaymentStatus() == PaymentStatusEnum.UNPAID;
    }
}
