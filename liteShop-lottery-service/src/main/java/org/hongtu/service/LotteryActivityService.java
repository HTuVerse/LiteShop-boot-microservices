package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.LotteryActivity;
import org.hongtu.Result.Result;


import java.util.List;

/**
 * <p>
 * 抽奖活动表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
public interface LotteryActivityService extends IService<LotteryActivity> {

    /**
     * 获取所有抽奖活动（包括禁用的）
     * @return 活动列表
     */
    Result<List<LotteryActivity>> getAllActivities();

    /**
     * 获取所有启用的抽奖活动
     * @return 活动列表
     */
    Result<List<LotteryActivity>> getEnabledActivities();

    /**
     * 新增活动
     * @param activity 活动信息
     * @return 是否成功
     */
    Result<Boolean> addActivity(LotteryActivity activity);

    /**
     * 编辑活动（完整编辑）
     * @param activity 活动信息
     * @return 是否成功
     */
    Result<Boolean> editActivity(LotteryActivity activity);

    /**
     * 切换活动状态
     * @param activityId 活动ID
     * @return 是否成功
     */
    Result<Boolean> toggleActivityStatus(Long activityId);

    /**
     * 删除活动
     * @param activityId 活动ID
     * @return 是否成功
     */
    Result<Boolean> deleteActivity(Long activityId);

    /**
     * 检查活动是否存在且启用
     * @param activityId 活动ID
     * @return 是否存在且启用
     */
    boolean isActivityEnabled(Long activityId);
}
