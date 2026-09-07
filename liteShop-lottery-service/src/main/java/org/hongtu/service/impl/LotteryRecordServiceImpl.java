package org.hongtu.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.VO.ActivityLotteryPrizeVO;
import org.hongtu.VO.DrawResult;
import org.hongtu.constant.RabbitMQConstants;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.CouponMsgOperateTypeEnum;
import org.hongtu.enums.PrizeTypeEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.service.*;
import org.hongtu.utils.BeanCopyUtils;
import org.hongtu.utils.LotteryUtil;
import org.hongtu.utils.RabbitMQUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 抽奖记录表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Slf4j
@Service
public class LotteryRecordServiceImpl extends ServiceImpl<LotteryRecordMapper, LotteryRecord> implements LotteryRecordService {

    @Resource
    private LotteryPrizeMapper lotteryPrizeMapper;

    @Resource
    private LotteryActivityMapper lotteryActivityMapper;

    @Resource
    private ActivityPrizeMapper activityPrizeMapper;

    @Resource
    private UserActivityChanceMapper userActivityChanceMapper;

    @Resource
    private ActivityPrizeService activityPrizeService;

    @Resource
    UserActivityChanceService userActivityChanceService;

    @Resource
    private UserFeignClient userFeignClient;

    @Resource
    RabbitMQUtils rabbitMQUtils;

    /**
     * 最大重试次数，防止无限循环
     */
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<LotteryRecord> drawLottery(Long userId, Long activityId) {
        // 1. 参数校验
        if (userId == null || activityId == null || activityId <= 0) {
            return Result.error("参数错误");
        }

        // 2. 【优化】先扣减用户抽奖次数（使用乐观锁防止并发问题）
        // 将扣减操作提前到最开始，避免查询期间的并发问题
        boolean chanceDeducted = userActivityChanceService.decreaseUserChances(userId, activityId, 1);
        if (!chanceDeducted) {
            return Result.error("抽奖次数不足或扣减失败");
        }

        try {
            // 3. 检查活动是否存在且有效
            LotteryActivity activity = lotteryActivityMapper.selectById(activityId);
            if (activity == null || activity.getStatus() != BooleanEnum.YES) {
                throw new BusinessException("活动不存在或未开启");
            }

            // 4. 检查活动时间是否有效
            LocalDateTime now = LocalDateTime.now();
            if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
                throw new BusinessException("活动尚未开始");
            }
            if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
                throw new BusinessException("活动已结束");
            }

            // 5. 获取活动关联的所有奖品
            Result<List<ActivityLotteryPrizeVO>> activityPrizesResult = activityPrizeService.getActivityPrizes(activityId);
            if (activityPrizesResult.getData() == null || activityPrizesResult.getData().isEmpty()) {
                throw new BusinessException("没有可用奖品");
            }

            // 6. 转换数据并执行抽奖
            List<LotteryPrize> prizes = BeanCopyUtils.copyBeanList(activityPrizesResult.getData(), LotteryPrize.class);
            List<ActivityPrize> activityPrizes = activityPrizeService.getInventoriesByActivityId(activityId);

            // 7. 使用循环重试机制执行抽奖
            DrawResult drawResult = null;
            boolean inventoryDeducted = false;

            for (int retryCount = 0; retryCount < MAX_RETRY_COUNT; retryCount++) {
                // 获取最新的库存情况
                activityPrizes = activityPrizeService.getInventoriesByActivityId(activityId);

                // 执行抽奖算法（基于概率和库存）
                drawResult = LotteryUtil.drawWithProbabilityAndStock(prizes, activityPrizes);

                if (!drawResult.isWon()) {
                    // 没有可用奖品，记录未中奖
                    log.info("抽奖未中奖：userId={}, activityId={}, 原因={}",
                            userId, activityId, drawResult.getMessage());
                    break;
                }

                // 尝试扣减库存（用户抽奖扣减，只扣剩余库存）
                int deductResult = activityPrizeMapper.deductSurplusInventory(
                        activityId,
                        drawResult.getPrize().getId(),
                        1L
                );

                if (deductResult > 0) {
                    inventoryDeducted = true;
                    break;
                }

                // 库存扣减失败，继续重试
                log.warn("库存扣减失败，重试第{}次，userId={}, activityId={}, prizeId={}",
                        retryCount + 1, userId, activityId, drawResult.getPrize().getId());
            }

            // 8. 创建抽奖记录
            LotteryRecord record = new LotteryRecord();
            record.setUserId(userId);
            record.setActivityId(activityId);
            record.setCreateTime(LocalDateTime.now());

            if (drawResult != null && drawResult.isWon() && inventoryDeducted) {
                // 中奖处理
                LotteryPrize winPrize = drawResult.getPrize();
                record.setPrizeId(winPrize.getId());
                record.setPrizeName(winPrize.getName());
                record.setPrizeType(winPrize.getType());

                // 根据奖品类型处理
                // 【修复】修正判断逻辑：prizeResult不为null且code不等于200时才表示失败
                Result<Void> prizeResult = handlePrizeDistribution(userId, winPrize, record);
                if (prizeResult != null && !Objects.equals(prizeResult.getCode(), Result.CODE_SUCCESS)) {
                    // 奖品发放失败，但抽奖记录仍然保存，后续可补发
                    log.error("奖品发放失败，userId={}, prizeId={}, reason={}",
                            userId, winPrize.getId(), prizeResult.getMsg());
                    record.setIsReceived(BooleanEnum.NO);
                }
            } else {
                // 未中奖记录
                record.setPrizeType(PrizeTypeEnum.THANK_YOU_PARTICIPATION);
                record.setPrizeName(PrizeTypeEnum.THANK_YOU_PARTICIPATION.getDesc());
                record.setIsReceived(null);
            }

            save(record);
            return Result.success(record);

        } catch (BusinessException e) {
            // 业务异常，抛出以触发事务回滚
            log.warn("抽奖业务校验失败，userId={}, activityId={}, reason={}", userId, activityId, e.getMessage());
            throw e;
        } catch (Exception e) {
            // 系统异常，抛出以触发事务回滚
            log.error("抽奖过程中发生错误，userId={}, activityId={}", userId, activityId, e);
            throw new BusinessException(500, "抽奖失败，请稍后再试", e);
        }
    }

    /**
     * 处理奖品发放
     */
    private Result<Void> handlePrizeDistribution(Long userId, LotteryPrize winPrize, LotteryRecord record) {
        if (winPrize.getType() == null) {
            record.setIsReceived(null);
            return null;
        }

        switch (winPrize.getType()) {
            case COUPON:
                return handleCouponPrize(userId, winPrize, record);
            case POINTS:
                return handlePointsPrize(userId, winPrize, record);
            case REALITY:
                // 实物奖品需要用户手动领取
                record.setIsReceived(BooleanEnum.NO);
                return null;
            default:
                record.setIsReceived(null);
                return null;
        }
    }

    /**
     * 处理优惠券奖品
     * 使用异步发送方式，不阻塞抽奖流程
     * 优惠券发放状态先标记为"待发放"，由消费者处理后更新状态
     */
    private Result<Void> handleCouponPrize(Long userId, LotteryPrize winPrize, LotteryRecord record) {
        CouponMessageDTO couponMessage = new CouponMessageDTO();
        couponMessage.setCouponId(winPrize.getCouponId());
        couponMessage.setUserId(userId);
        couponMessage.setStartTime(LocalDateTimeUtil.of(DateUtil.beginOfDay(DateUtil.tomorrow())));
        couponMessage.setExpiredTime(LocalDateTimeUtil.of(DateUtil.endOfDay(DateUtil.nextWeek())));
        couponMessage.setOperationType(CouponMsgOperateTypeEnum.SEND_OF_MSG);

        try {
            // 使用异步发送，不等待确认，避免阻塞抽奖接口
            rabbitMQUtils.sendMessage(
                    RabbitMQConstants.Coupon.EXCHANGE,
                    RabbitMQConstants.Coupon.ISSUE_KEY,
                    couponMessage
            );
            log.info("优惠券发放消息已发送，userId={}, couponId={}", userId, winPrize.getCouponId());
            // 消息已发送，标记为已发放（实际发放由消费者处理）
            record.setIsReceived(BooleanEnum.YES);
            return null;
        } catch (Exception e) {
            log.error("发送优惠券消息异常，userId={}, couponId={}", userId, winPrize.getCouponId(), e);
            record.setIsReceived(BooleanEnum.NO);
            return Result.error("优惠券发送异常");
        }
    }

    /**
     * 处理积分奖品
     */
    private Result<Void> handlePointsPrize(Long userId, LotteryPrize winPrize, LotteryRecord record) {
        try {
            Integer pointsToAdd = winPrize.getPoints() != null ? winPrize.getPoints() : 0;
            // 微服务改造：经 Feign 调 user-service 加积分（与原行为一致，仅调用异常时进入 catch）
            userFeignClient.addUserPoints(userId, pointsToAdd);
            record.setIsReceived(BooleanEnum.YES);
            return null;
        } catch (Exception e) {
            log.error("积分发放失败，userId={}, points={}", userId, winPrize.getPoints(), e);
            record.setIsReceived(BooleanEnum.NO);
            return Result.error("积分发放失败");
        }
    }

    @Override
    public Result<Page<LotteryRecord>> getUserLotteryRecordsByActivity(Long userId, Long activityId, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<LotteryRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LotteryRecord::getUserId, userId)
                .eq(LotteryRecord::getActivityId, activityId)
                .orderByDesc(LotteryRecord::getCreateTime);

        Page<LotteryRecord> page = new Page<>(currentPage, pageSize);
        return Result.success(page(page, queryWrapper));
    }

    @Override
    public Result<Page<LotteryRecord>> getActivityAllRecords(Long activityId, Long userId, Integer prizeType, Integer isReceived, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<LotteryRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LotteryRecord::getActivityId, activityId);
        // 如果指定了userId，则只查询该用户的记录
        if (userId != null) {
            queryWrapper.eq(LotteryRecord::getUserId, userId);
        }
        // 如果指定了prizeType，则只查询该奖品类型的记录
        if (prizeType != null) {
            PrizeTypeEnum prizeTypeEnum = PrizeTypeEnum.fromCode(prizeType);
            if (prizeTypeEnum != null) {
                queryWrapper.eq(LotteryRecord::getPrizeType, prizeTypeEnum);
            }
        }
        // 如果指定了isReceived，则只查询该领取状态的记录
        if (isReceived != null) {
            BooleanEnum receivedEnum = BooleanEnum.fromCode(isReceived);
            if (receivedEnum != null) {
                queryWrapper.eq(LotteryRecord::getIsReceived, receivedEnum);
            }
        }
        queryWrapper.orderByDesc(LotteryRecord::getCreateTime);

        Page<LotteryRecord> page = new Page<>(currentPage, pageSize);
        return Result.success(page(page, queryWrapper));
    }

    @Override
    @Transactional
    public boolean receivePrize(Long userId, Long recordId) {
        // 1. 查询记录是否存在
        LotteryRecord record = getById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            return false;
        }

        // 2. 检查是否已领取（使用Objects.equals防止NPE）
        if (Objects.equals(record.getIsReceived(), BooleanEnum.YES)) {
            return false;
        }

        // 3. 更新领取状态（使用乐观锁防止并发问题）
        LambdaUpdateWrapper<LotteryRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LotteryRecord::getId, recordId)
                .eq(LotteryRecord::getUserId, userId)
                .eq(LotteryRecord::getIsReceived, BooleanEnum.NO)
                .set(LotteryRecord::getIsReceived, BooleanEnum.YES)
                .set(LotteryRecord::getReceiveTime, LocalDateTime.now());

        return update(updateWrapper);
    }

    @Override
    public boolean clearActivityRecords(Long activityId) {
        LambdaQueryWrapper<LotteryRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LotteryRecord::getActivityId, activityId);
        return remove(queryWrapper);
    }
}
