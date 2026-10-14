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

    @Scheduled(fixedDelay = 5000)
    public void publishPending() {
        List<LotteryPrizeOutbox> pending = TenantIgnore.get(() -> lotteryPrizeOutboxMapper.selectList(
                new LambdaQueryWrapper<LotteryPrizeOutbox>()
                        .eq(LotteryPrizeOutbox::getStatus, "PENDING")
                        .lt(LotteryPrizeOutbox::getAttempts, 8)
                        .last("LIMIT 50")));
        for (LotteryPrizeOutbox event : pending) {
            if (event.getTenantId() == null) {
                continue;
            }
            TenantScope.run(event.getTenantId(), () -> publishOne(event));
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
                    lotteryPrizeOutboxMapper.updateById(event);
                    return;
                }
                event.setStatus("SENT");
                lotteryPrizeOutboxMapper.updateById(event);
                return;
            }
            if ("POINTS".equals(event.getPrizeType())) {
                JSONObject payload = JSONUtil.parseObj(event.getPayload());
                Result<Integer> result = userFeignClient.addUserPointsIdempotent(
                        payload.getLong("userId"), payload.getInt("points"), event.getEventId());
                if (result == null || !Result.CODE_SUCCESS.equals(result.getCode())) {
                    event.setStatus("FAILED");
                    event.setLastError(result == null ? "积分接口无响应" : result.getMsg());
                    lotteryPrizeOutboxMapper.updateById(event);
                    return;
                }
                markRecordReceived(event);
            }
        } catch (Exception exception) {
            event.setLastError(exception.getMessage());
            lotteryPrizeOutboxMapper.updateById(event);
            log.error("发奖投递失败: eventId={}", event.getEventId(), exception);
        }
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
        event.setStatus("DONE");
        event.setUpdatedAt(LocalDateTime.now());
        lotteryPrizeOutboxMapper.updateById(event);
    }
}
