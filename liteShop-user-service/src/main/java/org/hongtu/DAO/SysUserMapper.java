package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hongtu.DTO.SysUser;

/**
 * <p>
 * 系统用户表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 锁定本租户用户积分行，保证积分流水前后余额准确。 */
    @Select("SELECT * FROM sys_user WHERE id = #{userId} AND tenant_id = #{tenantId} FOR UPDATE")
    SysUser lockMallPointsUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 根据唯一标识符（openId）查询用户id
     *
     * @param openId 用户ID
     * @return 角色编码集合
     */
    @Select("SELECT id FROM sys_user WHERE tenant_id = #{tenantId} AND open_id = #{openId}")
    Long existsUserByOpenId(@Param("tenantId") Long tenantId, @Param("openId") String openId);

    /**
     * 根据手机号（phone）查询用户id
     *
     * @param phone 手机号码
     * @return 角色编码集合
     */
    @Select("SELECT id FROM sys_user WHERE phone = #{phone}")
    Long getUserIdByPhone(@Param("phone") String phone);

    /**
     * 增加用户积分
     *
     * @param userId 用户ID
     * @param points 增加的积分数量
     * @return 受影响的行数
     */
    @Update("UPDATE sys_user SET points = points + #{points}, update_time = NOW() WHERE id = #{userId}")
    int addUserPoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 减少用户积分
     *
     * @param userId 用户ID
     * @param points 增加的积分数量
     * @return 受影响的行数
     */
    @Update("UPDATE sys_user SET points = points - #{points}, update_time = NOW() WHERE id = #{userId}")
    int subUserPoints(@Param("userId") Long userId, @Param("points") Integer points);

    /** 兑换扣减：同时校验租户和余额，避免并发超扣。 */
    @Update("UPDATE sys_user SET points = points - #{points}, update_time = NOW() WHERE id = #{userId} AND tenant_id = #{tenantId} AND points >= #{points}")
    int deductMallPoints(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("points") Integer points);

    /** 兑换失败补偿积分。 */
    @Update("UPDATE sys_user SET points = points + #{points}, update_time = NOW() WHERE id = #{userId} AND tenant_id = #{tenantId}")
    int refundMallPoints(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("points") Integer points);

    /** 并发安全地扣减余额，余额不足时不更新。 */
    @Update("UPDATE sys_user SET surplus_money = COALESCE(surplus_money, 0) - #{amount}, update_time = NOW() " +
            "WHERE id = #{userId} AND COALESCE(surplus_money, 0) >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);

    /** 并发安全地增加余额。 */
    @Update("UPDATE sys_user SET surplus_money = COALESCE(surplus_money, 0) + #{amount}, update_time = NOW() " +
            "WHERE id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);

}
