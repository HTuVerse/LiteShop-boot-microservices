package org.hongtu.DAO;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.hongtu.DTO.Coupon;

/**
 * <p>
 * 优惠券 Mapper 接口
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 扣减优惠券库存（使用乐观锁，确保库存充足时才扣减）
     * @param couponId 优惠券ID
     * @param count 扣减数量
     * @return 影响行数，大于0表示扣减成功
     */
    @Update("UPDATE coupon SET remain_count = remain_count - #{count} " +
            "WHERE id = #{couponId} AND remain_count >= #{count}")
    int decreaseStock(@Param("couponId") Long couponId, @Param("count") Integer count);

    /**
     * 恢复优惠券库存
     * @param couponId 优惠券ID
     * @param count 恢复数量
     * @return 影响行数
     */
    @Update("UPDATE coupon SET remain_count = remain_count + #{count} " +
            "WHERE id = #{couponId}")
    int increaseStock(@Param("couponId") Long couponId, @Param("count") Integer count);

    /** 按旧库存条件原子调整可发库存及总量，保持已发放数量不变。 */
    @Update("UPDATE coupon SET remain_count = remain_count + #{delta}, total_count = total_count + #{delta} " +
            "WHERE id = #{couponId} AND remain_count = #{beforeCount} " +
            "AND remain_count + #{delta} >= 0 AND total_count + #{delta} >= 0")
    int adjustStock(@Param("couponId") Long couponId, @Param("beforeCount") Integer beforeCount,
                    @Param("delta") Integer delta);

    /**
     * 批量下架过期的优惠券
     * 将已过期但仍为上架状态的优惠券自动下架
     * @return 更新的记录数
     */
    @Update("UPDATE coupon SET status = 0 " +
            "WHERE status = 1 AND end_time < NOW()")
    int batchOffShelfExpiredCoupons();

}
