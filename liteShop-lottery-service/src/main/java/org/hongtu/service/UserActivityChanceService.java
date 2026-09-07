package org.hongtu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.UserActivityChance;
import org.hongtu.Result.Result;

import java.util.List;

/**
 * <p>
 * 用户抽奖机会活动关联表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
public interface UserActivityChanceService extends IService<UserActivityChance> {

    /**
     * 增加用户抽奖次数
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @param chances 增加的次数
     * @return 是否成功
     */
    boolean increaseUserChances(Long userId, Long activityId, Integer chances);

    /**
     * 减少用户抽奖次数
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @param chances 减少的次数
     * @return 是否成功
     */
    boolean decreaseUserChances(Long userId, Long activityId, Integer chances);

    /**
     * 分页获取活动的所有用户抽奖机会
     *
     * @param activityId 活动ID
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Result<Page<UserActivityChance>> getActivityUserChances(Long activityId, Integer currentPage, Integer pageSize);

    /**
     * 批量增加用户抽奖机会
     *
     * @param activityId 活动ID
     * @param chances 增加的次数
     * @param allUsers 是否全体用户
     * @param userIds 用户ID列表（allUsers为false时有效）
     * @return 是否成功
     */
    boolean batchIncreaseChances(Long activityId, Integer chances, Boolean allUsers, List<Long> userIds);

}
