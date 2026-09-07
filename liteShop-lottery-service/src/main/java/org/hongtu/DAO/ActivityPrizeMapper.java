package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.hongtu.DTO.ActivityPrize;

/**
 * <p>
 * 活动奖品关联表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Mapper
public interface ActivityPrizeMapper extends BaseMapper<ActivityPrize> {

    /**
     * 【用户抽奖使用】扣减剩余库存（使用乐观锁方式，防止超卖）
     * 只扣减剩余库存，不影响总库存（总库存用于统计）
     *
     * @param activityId 活动ID
     * @param prizeId 奖品ID
     * @param amount 扣减数量
     * @return 影响的行数，大于0表示更新成功
     */
    @Update("UPDATE activity_prize SET surplus_stock = surplus_stock - #{amount}, update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND prize_id = #{prizeId} AND surplus_stock >= #{amount}")
    int deductSurplusInventory(@Param("activityId") Long activityId, 
                               @Param("prizeId") Long prizeId, 
                               @Param("amount") Long amount);

    /**
     * 【管理员使用】扣减库存（同时扣减总库存和剩余库存）
     * 用于管理员手动调整库存场景
     *
     * @param activityId 活动ID
     * @param prizeId 奖品ID
     * @param amount 扣减数量
     * @return 影响的行数，大于0表示更新成功
     */
    @Update("UPDATE activity_prize SET surplus_stock = surplus_stock - #{amount}, " +
            "total_stock = total_stock - #{amount}, update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND prize_id = #{prizeId} " +
            "AND surplus_stock >= #{amount} AND total_stock >= #{amount}")
    int deductInventoryByAdmin(@Param("activityId") Long activityId, 
                               @Param("prizeId") Long prizeId, 
                               @Param("amount") Long amount);

    /**
     * 【管理员使用】增加库存（同时增加总库存和剩余库存）
     *
     * @param activityId 活动ID
     * @param prizeId 奖品ID
     * @param amount 增加数量
     * @return 影响的行数，大于0表示更新成功
     */
    @Update("UPDATE activity_prize SET surplus_stock = surplus_stock + #{amount}, " +
            "total_stock = total_stock + #{amount}, update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND prize_id = #{prizeId}")
    int increaseInventory(@Param("activityId") Long activityId,
                          @Param("prizeId") Long prizeId,
                          @Param("amount") Long amount);

    /**
     * 【用户抽奖失败回滚使用】恢复剩余库存
     * 当抽奖流程失败需要回滚时使用
     *
     * @param activityId 活动ID
     * @param prizeId 奖品ID
     * @param amount 恢复数量
     * @return 影响的行数，大于0表示更新成功
     */
    @Update("UPDATE activity_prize SET surplus_stock = surplus_stock + #{amount}, update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND prize_id = #{prizeId}")
    int restoreSurplusInventory(@Param("activityId") Long activityId,
                                @Param("prizeId") Long prizeId,
                                @Param("amount") Long amount);
}
