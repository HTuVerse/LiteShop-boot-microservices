package org.hongtu.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.LotteryPrizeOutboxMapper;
import org.hongtu.DAO.LotteryRecordMapper;
import org.hongtu.DTO.CouponMessageDTO;
import org.hongtu.DTO.LotteryPrizeOutbox;
import org.hongtu.DTO.LotteryRecord;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.constant.RabbitMQConstants;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.CouponMsgOperateTypeEnum;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.hongtu.utils.RabbitMQUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事务提交后的发奖投递。确认失败保持未领取，并用同一个 eventId 重试。
 */
@Slf4j
@Component
public class LotteryPrizePublisher {

    @Resource
    private LotteryPrizeOutboxMapper lotteryPrizeOutboxMapper;
    @Resource
    private LotteryRecordMapper lotteryRecordMapper;
    @Resource
    private RabbitMQUtils rabbitMQUtils;
    @Resource
    private UserFeignClient userFeignClient;

    @Value("${liteshop.lottery.prize-confirm-timeout-seconds:120}")
    private long confirmTimeoutSeconds;

    @Scheduled(fixedDelay = 5000)
    public void publishPending() {
        reconcileStale();
        List<LotteryPrizeOutbox> pending = TenantIgnore.get(() -> lotteryPrizeOutboxMapper.selectList(
                new LambdaQueryWrapper<LotteryPrizeOutbox>()
                        .eq(LotteryPrizeOutbox::getStatus, "PENDING")
                        .lt(LotteryPrizeOutbox::getAttempts, 8)
                        .orderByAsc(LotteryPrizeOutbox::getUpdatedAt)
                        .last("LIMIT 50")));
        for (LotteryPrizeOutbox event : pending) {
            if (event.getTenantId() == null) {
                continue;
            }
            TenantScope.run(event.getTenantId(), () -> publishOne(event));
        }
    }

    /** 已投递但长期无业务回执时使用同一 eventId 重投；幂等凭据防止重复发券。 */
    private void reconcileStale() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(Math.max(30, confirmTimeoutSeconds));
        List<LotteryPrizeOutbox> stale = TenantIgnore.get(() -> lotteryPrizeOutboxMapper.selectList(
                new LambdaQueryWrapper<LotteryPrizeOutbox>()
                        .and(w -> w.eq(LotteryPrizeOutbox::getStatus, "SENT")
                                .lt(LotteryPrizeOutbox::getUpdatedAt, cutoff)
                                .or().eq(LotteryPrizeOutbox::getStatus, "PENDING")
                                .ge(LotteryPrizeOutbox::getAttempts, 8))
                        .orderByAsc(LotteryPrizeOutbox::getUpdatedAt)
                        .last("LIMIT 50")));
        for (LotteryPrizeOutbox event : stale) {
            if (event.getTenantId() == null) continue;
            TenantScope.run(event.getTenantId(), () -> {
                boolean exhausted = event.getAttempts() != null && event.getAttempts() >= 8;
                String next = exhausted ? "FAILED" : "PENDING";
                int changed = lotteryPrizeOutboxMapper.update(null, new LambdaUpdateWrapper<LotteryPrizeOutbox>()
                        .eq(LotteryPrizeOutbox::getId, event.getId())
                        .eq(LotteryPrizeOutbox::getStatus, event.getStatus())
                        .eq(LotteryPrizeOutbox::getUpdatedAt, event.getUpdatedAt())
                        .set(LotteryPrizeOutbox::getStatus, next)
                        .set(LotteryPrizeOutbox::getUpdatedAt, LocalDateTime.now())
                        .set(LotteryPrizeOutbox::getLastError, exhausted ? "发奖重试次数耗尽，需人工处理死信" : "回执超时，等待重投"));
                if (changed == 1 && exhausted) {
                    log.error("发奖重试耗尽，需人工核对死信并处理: eventId={}, tenantId={}",
                            event.getEventId(), event.getTenantId());
                }
            });
        }
    }

    public void markPrizeReceived(String eventId) {
        LotteryPrizeOutbox event = TenantIgnore.get(() -> lotteryPrizeOutboxMapper.selectOne(
                new LambdaQueryWrapper<LotteryPrizeOutbox>()
                        .eq(LotteryPrizeOutbox::getEventId, eventId)
                        .last("LIMIT 1")));
        if (event == null || event.getTenantId() == null) {
            return;
        }
        TenantScope.run(event.getTenantId(), () -> markRecordReceived(event));
    }

    private void publishOne(LotteryPrizeOutbox event) {
        event.setAttempts((event.getAttempts() == null ? 0 : event.getAttempts()) + 1);
        event.setUpdatedAt(LocalDateTime.now());
        try {
            if ("COUPON".equals(event.getPrizeType())) {
                if (!rabbitMQUtils.sendMessageWithConfirm(
                        RabbitMQConstants.Coupon.EXCHANGE,
                        RabbitMQConstants.Coupon.ISSUE_KEY,
                        couponMessage(event))) {
                    event.setLastError("消息服务器未确认");
                    updatePending(event);
                    return;
                }
                event.setStatus("SENT");
                updatePending(event);
                return;
            }
            if ("POINTS".equals(event.getPrizeType())) {
                JSONObject payload = JSONUtil.parseObj(event.getPayload());
                Result<Integer> result = userFeignClient.addUserPointsIdempotent(
                        payload.getLong("userId"), payload.getInt("points"), event.getEventId());
                if (result == null || !Result.CODE_SUCCESS.equals(result.getCode())) {
                    event.setStatus("FAILED");
                    event.setLastError(result == null ? "积分接口无响应" : result.getMsg());
                    updatePending(event);
                    log.error("积分发奖被远程服务拒绝，需人工核对: eventId={}", event.getEventId());
                    return;
                }
                markRecordReceived(event);
            }
        } catch (Exception exception) {
            event.setLastError(exception.getMessage());
            updatePending(event);
            log.error("发奖投递失败: eventId={}", event.getEventId(), exception);
        }
    }

    /** 回执可能先于 MQ confirm 到达，因此只能更新仍为 PENDING 的行。 */
    private void updatePending(LotteryPrizeOutbox event) {
        lotteryPrizeOutboxMapper.update(null, new LambdaUpdateWrapper<LotteryPrizeOutbox>()
                .eq(LotteryPrizeOutbox::getId, event.getId())
                .eq(LotteryPrizeOutbox::getStatus, "PENDING")
                .set(LotteryPrizeOutbox::getStatus, event.getStatus())
                .set(LotteryPrizeOutbox::getAttempts, event.getAttempts())
                .set(LotteryPrizeOutbox::getUpdatedAt, event.getUpdatedAt())
                .set(LotteryPrizeOutbox::getLastError, event.getLastError()));
    }

    private CouponMessageDTO couponMessage(LotteryPrizeOutbox event) {
        JSONObject payload = JSONUtil.parseObj(event.getPayload());
        CouponMessageDTO message = new CouponMessageDTO();
        message.setEventId(event.getEventId());
        message.setUserId(payload.getLong("userId"));
        message.setCouponId(payload.getLong("couponId"));
        message.setOperationType(CouponMsgOperateTypeEnum.SEND_OF_MSG);
        return message;
    }

    private void markRecordReceived(LotteryPrizeOutbox event) {
        lotteryRecordMapper.update(null, new LambdaUpdateWrapper<LotteryRecord>()
                .eq(LotteryRecord::getId, event.getRecordId())
                .set(LotteryRecord::getIsReceived, BooleanEnum.YES));
        lotteryPrizeOutboxMapper.update(null, new LambdaUpdateWrapper<LotteryPrizeOutbox>()
                .eq(LotteryPrizeOutbox::getId, event.getId())
                .ne(LotteryPrizeOutbox::getStatus, "DONE")
                .set(LotteryPrizeOutbox::getStatus, "DONE")
                .set(LotteryPrizeOutbox::getUpdatedAt, LocalDateTime.now())
                .set(LotteryPrizeOutbox::getLastError, null));
    }
}
