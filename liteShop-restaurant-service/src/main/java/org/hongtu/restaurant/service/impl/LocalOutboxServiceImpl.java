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
        if (existing != null && ("DONE".equals(existing.getStatus()) || Boolean.TRUE.equals(existing.getCommitted()))) {
            return;
        }
        if (existing != null && !"COMPENSATED".equals(existing.getStatus())) {
            throw new IllegalStateException("上次跨服务操作仍在对账，请稍后重试");
        }
        saveStatus(eventType, eventId, payload, "PENDING", null);
        try {
            remoteCall.run();
        } catch (RuntimeException exception) {
            // Feign 超时无法证明远端失败，继续对账而不能终止事件。
            saveStatus(eventType, eventId, payload, "UNKNOWN", exception.getMessage());
            throw exception;
        }
        saveStatus(eventType, eventId, payload, "SENT", null);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (localOutboxMapper.markCommitted(TenantContext.getTenantId(), eventId) != 1) {
                throw new IllegalStateException("发件箱事务提交标记失败");
            }
        }
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
                .in(LocalOutbox::getStatus, "SENT", "PENDING", "UNKNOWN")
                .ne(LocalOutbox::getEventType, "MEMBER_REWARD_SYNC")
                .lt(LocalOutbox::getUpdatedAt, LocalDateTime.now().minusSeconds(60))
                .orderByAsc(LocalOutbox::getUpdatedAt)
                .last("LIMIT 100")));
        for (LocalOutbox event : stuck) {
            if (event.getTenantId() == null) {
                continue;
            }
            TenantScope.run(event.getTenantId(), () -> reconcileOne(event));
        }
    }

    private void reconcileOne(LocalOutbox event) {
        if (Boolean.TRUE.equals(event.getCommitted())) {
            saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "DONE", null);
            return;
        }
        compensate(event);
    }

    private void compensate(LocalOutbox event) {
        try {
            JSONObject payload = JSONUtil.parseObj(event.getPayload());
            if ("COUPON_VERIFY".equals(event.getEventType())) {
                String state = FeignResultUtil.unwrap(couponFeignClient.verificationState(
                        payload.getLong("userCouponId"), payload.getLong("orderId")));
                if ("ACTIVE".equals(state)) {
                    FeignResultUtil.check(couponFeignClient.restoreCouponOnCancel(
                            payload.getLong("userCouponId"), payload.getLong("orderId")));
                } else if ("ABSENT".equals(state)) {
                    // 查询时远端请求仍可能在途，保留事件直至观察到确定结果。
                    throw new IllegalStateException("优惠券核销尚未确认，等待下次对账");
                } else if (!"RESTORED".equals(state)) {
                    throw new IllegalStateException("未知优惠券核销状态: " + state);
                }
            } else if ("COUPON_RESTORE".equals(event.getEventType())) {
                String state = FeignResultUtil.unwrap(couponFeignClient.verificationState(
                        payload.getLong("userCouponId"), payload.getLong("orderId")));
                if ("RESTORED".equals(state)) {
                    FeignResultUtil.check(couponFeignClient.undoRestore(
                            payload.getLong("userCouponId"), payload.getLong("orderId")));
                } else if ("ACTIVE".equals(state)) {
                    // 原返券请求可能晚于状态查询完成，不能将未执行误记为已补偿。
                    throw new IllegalStateException("优惠券返还尚未确认，等待下次对账");
                } else if (!"ABSENT".equals(state)) {
                    throw new IllegalStateException("未知优惠券核销状态: " + state);
                }
            } else if ("BALANCE_CHANGE".equals(event.getEventType())) {
                compensateBalance(event, payload);
                return;
            } else {
                saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "NEEDS_REVIEW", "没有自动补偿策略");
                return;
            }
            saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "COMPENSATED", null);
        } catch (RuntimeException exception) {
            saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "UNKNOWN", exception.getMessage());
            log.error("发件箱补偿失败: eventId={}", event.getEventId(), exception);
        }
    }

    /** 先查询远端流水；退款入账撤销时须精确恢复赠送余额部分。 */
    private void compensateBalance(LocalOutbox event, JSONObject payload) {
        Long userId = payload.getLong("userId");
        String orderNo = payload.getStr("orderNo");
        Integer type = payload.getInt("type");
        Boolean applied = FeignResultUtil.unwrap(userFeignClient.balanceChangeApplied(orderNo, userId, type));
        if (applied == null) throw new IllegalStateException("余额流水对账结果为空");
        if (Integer.valueOf(3).equals(type) && payload.getLong("orderId") == null) {
            // 余额支付失败的独立补偿退款必须完成，即使原订单事务已回滚。
            if (!Boolean.TRUE.equals(applied)) {
                FeignResultUtil.check(userFeignClient.changeBalance(balanceChange(payload)));
            }
            saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "DONE", null);
            return;
        }
        if (!Boolean.TRUE.equals(applied)) {
            // 超时请求可能仍在远端执行；缺少流水不能证明调用失败。
            throw new IllegalStateException("余额变动尚未确认，等待下次对账");
        }
        if (Boolean.TRUE.equals(applied)) {
            if (Integer.valueOf(4).equals(type)) {
                boolean alreadyRefunded = Boolean.TRUE.equals(FeignResultUtil.unwrap(
                        userFeignClient.balanceChangeApplied(orderNo + "_COMPENSATE", userId, 3)));
                if (!alreadyRefunded) {
                    FeignResultUtil.check(userFeignClient.changeBalance(BalanceChangeDTO.builder()
                            .userId(userId).amount(payload.getBigDecimal("amount")).type(3)
                            .orderNo(orderNo + "_COMPENSATE").sourceOrderNo(orderNo)
                            .operateUserId(userId).build()));
                }
            } else if (Integer.valueOf(3).equals(type)) {
                if (!Boolean.TRUE.equals(FeignResultUtil.unwrap(userFeignClient.reverseBalanceCredit(
                        orderNo, userId, orderNo + ":outbox-reverse")))) {
                    throw new IllegalStateException("退款流水尚未确认，等待对账");
                }
            } else {
                throw new IllegalStateException("未知余额变动类型: " + type);
            }
        }
        saveStatus(event.getEventType(), event.getEventId(), event.getPayload(), "COMPENSATED", null);
    }

    private BalanceChangeDTO balanceChange(JSONObject payload) {
        return BalanceChangeDTO.builder()
                .userId(payload.getLong("userId"))
                .amount(payload.getBigDecimal("amount"))
                .type(payload.getInt("type"))
                .orderNo(payload.getStr("orderNo"))
                .sourceOrderNo(payload.getStr("sourceOrderNo"))
                .operateUserId(payload.getLong("userId"))
                .build();
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
                try {
                    TenantScope.run(tenantId, () -> saveStatus(null, eventId, null, "DONE", null));
                } catch (RuntimeException exception) {
                    // 提交标记已在业务事务中持久化，由对账任务补写 DONE。
                    log.error("业务已提交，发件箱完成状态待对账: eventId={}", eventId, exception);
                }
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
                created.setCommitted(false);
                created.setAttempts(1);
                created.setLastError(trim(error));
                created.setCreatedAt(now);
                created.setUpdatedAt(now);
                try {
                    localOutboxMapper.insert(created);
                } catch (DuplicateKeyException duplicate) {
                    throw new IllegalStateException("相同业务事件正在处理，请稍后重试", duplicate);
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
