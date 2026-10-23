package org.hongtu.restaurant.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.DAO.PendingPaymentReconcileMapper;
import org.hongtu.restaurant.DTO.OrderTenantRef;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 回调丢失时持续向支付渠道查单，按租户恢复已到账订单。 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class PendingPaymentReconcileJob implements Job {
    private static final String LOCK_KEY = "job:restaurant:payment-reconcile:lock";
    private static final String CURSOR_KEY = "job:restaurant:payment-reconcile:cursor";
    private static final int BATCH_SIZE = 30;
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    @Resource
    private PendingPaymentReconcileMapper pendingMapper;
    @Resource
    private RestaurantOrderService restaurantOrderService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 分布式锁限制并发，持久游标让每轮补查覆盖不同订单。 */
    @Override
    public void execute(JobExecutionContext context) {
        String owner = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, owner, 10, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            long cursor = readCursor();
            List<OrderTenantRef> batch = TenantIgnore.get(() -> pendingMapper.nextPending(cursor, BATCH_SIZE));
            if (batch.isEmpty()) {
                stringRedisTemplate.opsForValue().set(CURSOR_KEY, "0");
                return;
            }
            for (OrderTenantRef ref : batch) {
                Long renewed = stringRedisTemplate.execute(
                        RENEW_LOCK_SCRIPT, Collections.singletonList(LOCK_KEY), owner, "600");
                if (!Long.valueOf(1).equals(renewed)) {
                    log.warn("支付补查任务失去分布式锁，等待下一轮继续");
                    return;
                }
                if (ref.getId() == null || ref.getTenantId() == null) {
                    log.error("支付补查发现缺少订单或租户标识: {}", ref);
                    continue;
                }
                try {
                    TenantContext.setTenantId(ref.getTenantId());
                    if (restaurantOrderService.queryAndUpdatePaymentStatus(ref.getId())) {
                        log.info("支付补查确认到账: orderId={}, tenantId={}", ref.getId(), ref.getTenantId());
                    }
                } catch (Exception exception) {
                    log.warn("支付补查失败，将在下一轮重新扫描: orderId={}", ref.getId(), exception);
                } finally {
                    TenantContext.clear();
                }
            }
            stringRedisTemplate.opsForValue().set(CURSOR_KEY,
                    batch.size() < BATCH_SIZE ? "0" : String.valueOf(batch.get(batch.size() - 1).getId()));
        } catch (Exception exception) {
            log.error("支付补查任务失败", exception);
        } finally {
            TenantContext.clear();
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(LOCK_KEY), owner);
        }
    }

    private long readCursor() {
        String value = stringRedisTemplate.opsForValue().get(CURSOR_KEY);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(value.toString()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
