package org.hongtu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.LotteryRecord;
import org.hongtu.Result.Result;

/**
 * <p>
 * 抽奖记录表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
public interface LotteryRecordService extends IService<LotteryRecord> {

    /**
     * 执行抽奖
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @return 抽奖结果
     */
    Result<LotteryRecord> drawLottery(Long userId, Long activityId);

    /**
     * 获取用户在指定活动中的抽奖记录（分页）
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @return 分页抽奖记录
     */
    Result<Page<LotteryRecord>> getUserLotteryRecordsByActivity(Long userId, Long activityId, Integer currentPage, Integer pageSize);

    /**
     * 管理员获取指定活动的所有抽奖记录（分页，支持多条件筛选）
     *
     * @param activityId 活动ID
     * @param userId 用户ID（可选，指定时只查询该用户的记录）
     * @param prizeType 奖品类型（可选，0-优惠券，1-积分，2-实物，3-谢谢参与）
     * @param isReceived 领取状态（可选，0-未领取，1-已领取）
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @return 分页抽奖记录
     */
    Result<Page<LotteryRecord>> getActivityAllRecords(Long activityId, Long userId, Integer prizeType, Integer isReceived, Integer currentPage, Integer pageSize);

    /**
     * 领取奖品
     *
     * @param userId 用户ID
     * @param recordId 记录ID
     * @return 是否领取成功
     */
    boolean receivePrize(Long userId, Long recordId);


    /**
     * 清空指定活动的所有抽奖记录
     *
     * @param activityId 活动ID
     * @return 是否清空成功
     */
    boolean clearActivityRecords(Long activityId);
}
