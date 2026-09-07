package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.hongtu.DTO.UserActivityChance;

import java.util.List;

/**
 * <p>
 * 用户抽奖机会活动关联表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Mapper
public interface UserActivityChanceMapper extends BaseMapper<UserActivityChance> {

    /**
     * 获取用户在指定活动的抽奖机会数量
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @return 抽奖机会数量，如果没有记录则返回null
     */
    @Select("SELECT chances FROM user_activity_chance WHERE user_id = #{userId} AND activity_id = #{activityId}")
    Integer getUserChances(@Param("userId") Long userId, @Param("activityId") Long activityId);

    /**
     * 增加用户抽奖机会（已存在记录时使用）
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @param chances 增加的机会数量
     * @return 影响的行数，大于0表示更新成功
     */
    @Update("UPDATE user_activity_chance SET chances = chances + #{chances}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND activity_id = #{activityId}")
    int increaseUserChances(@Param("userId") Long userId,
                            @Param("activityId") Long activityId,
                            @Param("chances") Integer chances);

    /**
     * 【推荐使用】插入或更新用户抽奖机会（使用 ON DUPLICATE KEY UPDATE，防止并发问题）
     * 需要在表上创建唯一索引：UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`)
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @param chances 增加的机会数量
     * @return 影响的行数
     */
    @Insert("INSERT INTO user_activity_chance (user_id, activity_id, chances, create_time, update_time) " +
            "VALUES (#{userId}, #{activityId}, #{chances}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE chances = chances + #{chances}, update_time = NOW()")
    int insertOrUpdateChances(@Param("userId") Long userId,
                              @Param("activityId") Long activityId,
                              @Param("chances") Integer chances);

    /**
     * 减少用户抽奖机会（确保有足够的次数可减，使用乐观锁）
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @param chances 减少的机会数量
     * @return 影响的行数，大于0表示更新成功
     */
    @Update("UPDATE user_activity_chance SET chances = chances - #{chances}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND activity_id = #{activityId} AND chances >= #{chances}")
    int decreaseUserChances(@Param("userId") Long userId,
                            @Param("activityId") Long activityId,
                            @Param("chances") Integer chances);

    /**
     * 检查用户是否有抽奖机会
     *
     * @param userId 用户ID
     * @param activityId 活动ID
     * @return 大于0表示有抽奖机会
     */
    @Select("SELECT COUNT(*) FROM user_activity_chance WHERE user_id = #{userId} " +
            "AND activity_id = #{activityId} AND chances > 0")
    int hasChances(@Param("userId") Long userId, @Param("activityId") Long activityId);

    /**
     * 获取活动的所有用户抽奖机会，按机会数量降序排序
     *
     * @param activityId 活动ID
     * @return 用户抽奖机会列表
     */
    @Select("SELECT * FROM user_activity_chance WHERE activity_id = #{activityId} ORDER BY chances DESC")
    List<UserActivityChance> getActivityUserChances(@Param("activityId") Long activityId);

    /**
     * 批量为已存在记录的用户增加抽奖机会
     *
     * @param activityId 活动ID
     * @param chances 增加的机会数量
     * @return 影响的行数
     */
    @Update("UPDATE user_activity_chance SET chances = chances + #{chances}, update_time = NOW() " +
            "WHERE activity_id = #{activityId}")
    int batchIncreaseChancesByActivity(@Param("activityId") Long activityId,
                                       @Param("chances") Integer chances);

}
