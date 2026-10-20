package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hongtu.DTO.UserCoupon;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户优惠券关联表 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("SELECT count(*) FROM user_coupon WHERE user_id = #{userId} AND status IN (0, 1) " +
            "AND (expire_time IS NULL OR expire_time > NOW())")
    Long getUserTotalCoupons(@Param("userId") Long userId);

    /**
     * 批量更新过期优惠券状态
     * 将已过期但状态仍为未生效(0)或未使用(1)的优惠券更新为已过期(3)
     * @return 更新的记录数
     */
    @Update("UPDATE user_coupon SET status = 3 " +
            "WHERE status IN (0, 1) AND expire_time < NOW()")
    int batchUpdateExpiredStatus();

    /**
     * 批量更新已生效的优惠券状态
     * 将开始时间已到但状态仍为未生效(0)的优惠券更新为未使用(1)
     * @return 更新的记录数
     */
    @Update("UPDATE user_coupon SET status = 1 " +
            "WHERE status = 0 AND start_time <= NOW() AND (expire_time IS NULL OR expire_time > NOW())")
    int batchUpdateEffectiveStatus();

    /**
     * 统计过期超过指定天数的优惠券数量
     * @param days 过期天数
     * @return 过期优惠券数量
     */
    @Select("SELECT COUNT(*) FROM user_coupon " +
            "WHERE status IN (2, 3) AND expire_time < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int countLongExpiredCoupons(@Param("days") int days);

    /**
     * CAS乐观锁核销优惠券（防止并发一券多用）
     * 只有当前状态为"未使用(1)"时才更新为"已使用(2)"
     * @param id 用户优惠券ID
     * @param useTime 使用时间
     * @return 更新行数（0表示CAS失败，券已被其他请求核销）
     */
    @Update("UPDATE user_coupon SET status = 2, use_time = #{useTime} " +
            "WHERE id = #{id} AND status = 1")
    int casVerifyCoupon(@Param("id") Long id, @Param("useTime") LocalDateTime useTime);

    /**
     * CAS乐观锁返还优惠券（从已使用恢复为未使用）
     * @param id 用户优惠券ID
     * @param newStatus 恢复后的状态（1-未使用，3-已过期）
     * @return 更新行数
     */
    @Update("UPDATE user_coupon SET status = #{newStatus}, use_time = NULL " +
            "WHERE id = #{id} AND status = 2")
    int casRestoreCoupon(@Param("id") Long id, @Param("newStatus") int newStatus);

    /**
     * 返还优惠券时重置券码（避免与 verify_record 表唯一索引 verify_code 冲突）
     * @param id 用户优惠券ID
     * @param newCode 新的券码
     * @return 更新行数
     */
    @Update("UPDATE user_coupon SET code = #{newCode} WHERE id = #{id}")
    int updateCodeById(@Param("id") Long id, @Param("newCode") String newCode);

}
