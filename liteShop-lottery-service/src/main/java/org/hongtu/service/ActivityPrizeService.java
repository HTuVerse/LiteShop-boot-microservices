package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.ActivityPrize;
import org.hongtu.Result.Result;
import org.hongtu.VO.ActivityLotteryPrizeVO;
import org.hongtu.VO.ActivityPrizeUserVO;


import java.util.List;
import java.util.Map;

/**
 * <p>
 * 活动奖品关联表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
public interface ActivityPrizeService extends IService<ActivityPrize> {

    /**
     * 获取活动奖品列表（管理员使用，包含完整信息）
     *
     * @param activityId 活动ID
     * @return 奖品列表（包含概率、库存等完整信息）
     */
    Result<List<ActivityLotteryPrizeVO>> getActivityPrizes(Long activityId);

    /**
     * 获取活动奖品列表（用户端使用，隐藏敏感信息）
     *
     * @param activityId 活动ID
     * @return 奖品列表（不包含中奖概率等敏感信息）
     */
    Result<List<ActivityPrizeUserVO>> getActivityPrizesForUser(Long activityId);

    Result<Boolean> addPrizeToActivity(ActivityPrize activityPrize);

    Result<Boolean> batchAddPrizesToActivity(Long activityId, List<Long> prizeIds);

    /**
     * 获取活动关联的所有奖品库存
     *
     * @param activityId 活动ID
     * @return 奖品库存列表
     */
    List<ActivityPrize> getInventoriesByActivityId(Long activityId);

    /**
     * 获取活动奖品库存映射
     *
     * @param activityId 活动ID
     * @return 奖品ID -> 剩余库存 的映射
     */
    Map<Long, Long> getPrizeInventoryMap(Long activityId);


}
