package org.hongtu.restaurant.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.DAO.RestaurantOrderMapper;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 恢复支付创建结果未知的订单；只有渠道确认关闭才重新开放支付入口。 */
@Slf4j
@Configuration
@EnableScheduling
public class PaymentCreationReconcileJob {
    private static final String LOCK_KEY = "job:restaurant:payment-create-reconcile:lock";
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Resource private RestaurantOrderMapper orders;
    @Resource private RestaurantOrderService orderService;
    @Resource private StringRedisTemplate redis;

    @Scheduled(fixedDelay = 30000)
    public void reconcile() {
        String owner = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(LOCK_KEY, owner, 5, TimeUnit.MINUTES))) return;
        try {
            List<RestaurantOrder> stale = TenantIgnore.get(() -> orders.selectList(
                    new LambdaQueryWrapper<RestaurantOrder>()
                            .eq(RestaurantOrder::getPaymentStatus, PaymentStatusEnum.CREATING)
                            .lt(RestaurantOrder::getUpdatedAt, LocalDateTime.now().minusMinutes(2))
                            .orderByAsc(RestaurantOrder::getUpdatedAt).last("LIMIT 10")));
            for (RestaurantOrder order : stale) {
                if (order.getTenantId() == null) continue;
                try {
                    TenantScope.run(order.getTenantId(), () -> orderService.queryAndUpdatePaymentStatus(order.getId()));
                } catch (Exception error) {
                    log.warn("支付创建状态待再次对账: orderId={}", order.getId(), error);
                }
            }
        } catch (Exception error) {
            log.error("支付创建对账任务失败", error);
        } finally {
            redis.execute(RELEASE, Collections.singletonList(LOCK_KEY), owner);
        }
    }
}
