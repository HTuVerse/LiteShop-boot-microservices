package org.hongtu.service.consumer;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DAO.CouponMessageReceiptMapper;
import org.hongtu.DTO.CouponMessageDTO;
import org.hongtu.Result.Result;
import org.hongtu.constant.RabbitMQConstants;
import org.hongtu.service.UserCouponService;
import org.hongtu.service.LotteryCouponReceiptNotifier;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;

import java.util.HashMap;
import java.util.Map;

/**
 * 优惠券消息消费者服务
 */
@Slf4j
@Service
public class CouponMessageConsumer {

    @PostConstruct
    public void init() {
        log.info("========== 优惠券消息消费者已启动 ==========");
        log.info("监听队列: {}", RabbitMQConstants.Coupon.ISSUE_QUEUE);
        log.info("==========================================");
    }

    @Resource
    private UserCouponService userCouponService;
    @Resource
    private UserCouponMapper userCouponMapper;
    @Resource
    private CouponMessageReceiptMapper couponMessageReceiptMapper;
    @Resource
    private LotteryCouponReceiptNotifier lotteryNotifier;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private MessageConverter messageConverter;

    /**
     * 监听优惠券发放队列
     *
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConstants.Coupon.ISSUE_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void handleCouponIssue(CouponMessageDTO message, Message rawMessage) {
        log.info("收到优惠券发放消息: {}", JSON.toJSONString(message));
        // 参数无效和业务失败必须抛出，让容器按配置进入死信队列，不能吞掉后确认。
        if (message == null || message.getUserId() == null || message.getCouponId() == null
                || message.getEventId() == null || message.getEventId().isBlank()) {
            throw new IllegalArgumentException("优惠券发放消息参数无效");
        }
        if (couponMessageReceiptMapper.claim(message.getEventId(), TenantContext.getTenantId()) == 0) {
            log.info("优惠券发放消息已处理，跳过重复投递: eventId={}", message.getEventId());
            return;
        }
        try {
            // 处理优惠券发放逻辑
            Result result;
            // 如果时间参数为空，使用默认时间（立即生效，3天后过期）
            if (message.getStartTime() == null || message.getExpiredTime() == null) {
                result = userCouponService.sendCouponToUser(message.getUserId(), message.getCouponId());
            } else {
                result = userCouponService.sendCouponToUser(
                        message.getUserId(),
                        message.getCouponId(),
                        DateUtil.date(message.getStartTime()),
                        DateUtil.date(message.getExpiredTime())
                );
            }
            // 检查发放结果
            if (result != null && Result.CODE_SUCCESS.equals(result.getCode())) {
                log.info("优惠券发放成功，eventId={}, 优惠券ID: {}, 用户ID: {}",
                        message.getEventId(), message.getCouponId(), message.getUserId());
                if (message.getEventId().startsWith("lottery-")) {
                    Long tenantId = TenantContext.getTenantId();
                    String eventId = message.getEventId();
                    // 只有本地发券与幂等凭据都提交成功后，才可宣告抽奖奖品已领取。
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            TenantScope.run(tenantId, () -> lotteryNotifier.notifyOne(eventId));
                        }
                    });
                }
            } else {
                throw new IllegalStateException(result != null ? result.getMsg() : "优惠券发放返回空结果");
            }
        } catch (Exception e) {
            log.error("处理优惠券发放消息异常, couponId={}, userId={}",
                    message != null ? message.getCouponId() : null,
                    message != null ? message.getUserId() : null, e);
            throw new IllegalStateException("优惠券发放消息处理失败", e);
        }
    }

    /**
     * 监听优惠券过期队列
     *
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConstants.Coupon.EXPIRE_QUEUE)
    public void handleCouponExpire(CouponMessageDTO message, Message rawMessage) {
        log.info("收到优惠券过期消息: {}", JSON.toJSONString(message));
        try {
            // 参数校验
            if (message == null || message.getCouponId() == null || message.getUserId() == null) {
                log.warn("优惠券过期消息参数无效: {}", message);
                return;
            }
            // 处理优惠券过期逻辑
            userCouponService.expireUserCoupon(message.getCouponId(), message.getUserId());
            log.info("优惠券过期处理成功，优惠券ID: {}, 用户ID: {}", message.getCouponId(), message.getUserId());
        } catch (Exception e) {
            log.error("处理优惠券过期消息异常, couponId={}, userId={}",
                    message != null ? message.getCouponId() : null,
                    message != null ? message.getUserId() : null, e);
            throw new IllegalStateException("优惠券过期处理失败", e);
        }
    }

    /**
     * 处理优惠券数量查询请求
     */
    @RabbitListener(queues = RabbitMQConstants.Coupon.COUNT_QUERY_QUEUE)
    public void handleCouponCountQuery(Message message) {
        try {
            // 转换消息
            Map<String, Object> requestMap = (Map<String, Object>) messageConverter.fromMessage(message);
            Long userId = Long.valueOf(requestMap.get("userId").toString());
            String callbackQueue = requestMap.get("callbackQueue").toString();
            log.info("收到优惠券数量查询请求: userId={}, callbackQueue={}", userId, callbackQueue);
            // 查询优惠券数量
            Long couponCount = userCouponMapper.getUserTotalCoupons(userId);
            // 构建响应
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("userId", userId);
            responseMap.put("count", couponCount);
            responseMap.put("success", true);
            // 发送响应到回调队列
            rabbitTemplate.convertAndSend(callbackQueue, responseMap);
            log.info("已发送优惠券数量响应: userId={}, count={}", userId, couponCount);
        } catch (Exception e) {
            log.error("处理优惠券数量查询请求异常", e);
        }
    }
}
