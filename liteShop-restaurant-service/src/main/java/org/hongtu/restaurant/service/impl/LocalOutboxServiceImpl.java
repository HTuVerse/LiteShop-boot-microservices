package org.hongtu.restaurant.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.CouponFeignClient;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.api.dto.MemberRewardSyncDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.restaurant.DAO.LocalOutboxMapper;
import org.hongtu.restaurant.DAO.RestaurantOrderMapper;
import org.hongtu.restaurant.DTO.LocalOutbox;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.service.LocalOutboxService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 跨服务写操作的发件箱。远程调用前用独立事务记下事件，外层回滚后仍能自动补偿。
 */
@Slf4j
@Service
public class LocalOutboxServiceImpl implements LocalOutboxService {

    private static final int MAX_ATTEMPTS = 8;

    @Resource
    private LocalOutboxMapper localOutboxMapper;
    @Resource
    private RestaurantOrderMapper restaurantOrderMapper;
    @Resource
    private CouponFeignClient couponFeignClient;
    @Resource
    private UserFeignClient userFeignClient;

    private final TransactionTemplate requiresNew;

    public LocalOutboxServiceImpl(PlatformTransactionManager transactionManager) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void executeRemote(String eventType, String eventId, String payload, Runnable remoteCall) {
        LocalOutbox existing = find(eventId);
        if (existing != null && ("DONE".equals(existing.getStatus()) || "SENT".equals(existing.getStatus())
                || "COMPENSATED".equals(existing.getStatus()))) {
            return;
        }
        saveStatus(eventType, eventId, payload, "PENDING", null);
        try {
            remoteCall.run();
        } catch (RuntimeException exception) {
            saveStatus(eventType, eventId, payload, "FAILED", exception.getMessage());
            throw exception;
        }
        saveStatus(eventType, eventId, payload, "SENT", null);
        registerDoneAfterCommit(eventId);
    }

    @Override
    public void enqueueMemberReward(Long orderId, String eventId) {
        RestaurantOrder order = restaurantOrderMapper.selectById(orderId);
        if (order == null || order.getUserId() == null) return;
        LocalOutbox event = new LocalOutbox();
        event.setEventId(eventId);
        event.setEventType("MEMBER_REWARD_SYNC");
        event.setPayload(JSONUtil.createObj().set("orderId", orderId).toString());
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        try {
            localOutboxMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    TenantScope.run(tenantId, () -> deliverMemberReward(eventId));
                }
            });
        } else {
            deliverMemberReward(eventId);
        }
    }

    /** 始终读取订单最新实付金额，旧发件箱重试不会覆盖后续退款状态。 */
    private void deliverMemberReward(String eventId) {
        LocalOutbox event = find(eventId);
        if (event == null || "DONE".equals(event.getStatus())) return;
        try {
            Long orderId = JSONUtil.parseObj(event.getPayload()).getLong("orderId");
            RestaurantOrder order = restaurantOrderMapper.selectById(orderId);
            if (order == null || order.getUserId() == null) {
                saveStatus(null, eventId, null, "DONE", null);
                return;
            }
            boolean rewarded = order.getStatus() == OrderStatusEnum.COMPLETED
                    && (order.getPaymentStatus() == PaymentStatusEnum.PAID
                    || order.getPaymentStatus() == PaymentStatusEnum.PARTIAL_REFUND);
            BigDecimal amount = rewarded && order.getActualAmount() != null
                    ? order.getActualAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
            FeignResultUtil.check(userFeignClient.syncMemberReward(
                    new MemberRewardSyncDTO(order.getOrderNo(), order.getUserId(), amount)));
            saveStatus(null, eventId, null, "DONE", null);
        } catch (RuntimeException exception) {
            saveStatus(null, eventId, null, "PENDING", exception.getMessage());
            log.error("会员订单奖励同步失败，等待重试: eventId={}", eventId, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcile() {
        List<LocalOutbox> rewards = TenantIgnore.get(() -> localOutboxMapper.selectList(new LambdaQueryWrapper<LocalOutbox>()
                .eq(LocalOutbox::getEventType, "MEMBER_REWARD_SYNC")
                .eq(LocalOutbox::getStatus, "PENDING")
                .lt(LocalOutbox::getUpdatedAt, LocalDateTime.now().minusSeconds(60))
                .last("LIMIT 100")));
        for (LocalOutbox event : rewards) {
            if (event.getTenantId() != null)
                TenantScope.run(event.getTenantId(), () -> deliverMemberReward(event.getEventId()));
        }
        List<LocalOutbox> stuck = TenantIgnore.get(() -> localOutboxMapper.selectList(new LambdaQueryWrapper<LocalOutbox>()
                .in(LocalOutbox::getStatus, "SENT", "PENDING")
                .ne(LocalOutbox::getEventType, "MEMBER_REWARD_SYNC")
                .lt(LocalOutbox::getUpdatedAt, LocalDateTime.now().minusSeconds(60))
                .lt(LocalOutbox::getAttempts, MAX_ATTEMPTS)
                .last("LIMIT 100")));
        for (LocalOutbox event : stuck) {
            if (event.getTenantId() == null) {
                continue;
            }
            TenantScope.run(event.getTenantId(), () -> reconcileOne(event));
        }
    }

    private void reconcileOne(LocalOutbox event) {
        boolean orderExists = orderExists(event.getPayload());
        if ("SENT".equals(event.getStatus()) && orderExists) {
            saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "DONE", null);
            return;
        }
        if ("SENT".equals(event.getStatus())) {
            compensate(event);
            return;
        }
        saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "FAILED", "远程调用未确认");
    }

    private void compensate(LocalOutbox event) {
        try {
            JSONObject payload = JSONUtil.parseObj(event.getPayload());
            if ("COUPON_VERIFY".equals(event.getEventType())) {
                FeignResultUtil.check(couponFeignClient.restoreCouponOnCancel(
                        payload.getLong("userCouponId"), payload.getLong("orderId")));
            } else if ("BALANCE_CHANGE".equals(event.getEventType()) && Integer.valueOf(4).equals(payload.getInt("type"))) {
                FeignResultUtil.check(userFeignClient.changeBalance(BalanceChangeDTO.builder()
                        .userId(payload.getLong("userId"))
                        .amount(payload.getBigDecimal("amount"))
                        .type(3)
                        .orderNo(payload.getStr("orderNo") + ":outbox")
                        .operateUserId(payload.getLong("userId"))
                        .build()));
            } else if ("COUPON_RESTORE".equals(event.getEventType())) {
                saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "DONE", null);
                return;
            } else {
                saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "FAILED", "没有自动补偿策略");
                return;
            }
            saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "COMPENSATED", null);
        } catch (RuntimeException exception) {
            saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "FAILED", exception.getMessage());
            log.error("发件箱补偿失败: eventId={}", event.getEventId(), exception);
        }
    }

    private boolean orderExists(String payload) {
        JSONObject json = JSONUtil.parseObj(payload);
        Long orderId = json.getLong("orderId");
        if (orderId == null) {
            return false;
        }
        RestaurantOrder order = restaurantOrderMapper.selectById(orderId);
        return order != null;
    }

    private void registerDoneAfterCommit(String eventId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            saveStatus(null, eventId, null, "DONE", null);
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                TenantScope.run(tenantId, () -> saveStatus(null, eventId, null, "DONE", null));
            }
        });
    }

    private LocalOutbox find(String eventId) {
        return localOutboxMapper.selectOne(new LambdaQueryWrapper<LocalOutbox>()
                .eq(LocalOutbox::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private void saveStatus(String eventType, String eventId, String payload, String status, String error) {
        requiresNew.executeWithoutResult(transactionStatus -> {
            LocalOutbox current = find(eventId);
            LocalDateTime now = LocalDateTime.now();
            if (current == null) {
                LocalOutbox created = new LocalOutbox();
                created.setEventId(eventId);
                created.setEventType(eventType);
                created.setPayload(payload);
                created.setStatus(status);
                created.setAttempts(1);
                created.setLastError(trim(error));
                created.setCreatedAt(now);
                created.setUpdatedAt(now);
                try {
                    localOutboxMapper.insert(created);
                } catch (DuplicateKeyException duplicate) {
                    saveStatus(eventType, eventId, payload, status, error);
                }
                return;
            }
            current.setStatus(status);
            if (eventType != null) {
                current.setEventType(eventType);
            }
            if (payload != null) {
                current.setPayload(payload);
            }
            current.setAttempts((current.getAttempts() == null ? 0 : current.getAttempts()) + 1);
            current.setLastError(trim(error));
            current.setUpdatedAt(now);
            localOutboxMapper.updateById(current);
        });
    }

    private String trim(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 480 ? error.substring(0, 480) : error;
    }
}
