package org.hongtu.DAO;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 同一租户内可叠加优惠券的无向配对关系。 */
@Mapper
public interface CouponStackPairMapper {

    /** 查询一张券已明确允许叠加的其他券。 */
    @Select("SELECT CASE WHEN coupon_id_low = #{couponId} THEN coupon_id_high ELSE coupon_id_low END " +
            "FROM coupon_stack_pair WHERE tenant_id = #{tenantId} " +
            "AND (coupon_id_low = #{couponId} OR coupon_id_high = #{couponId})")
    List<Long> listPartnerIds(@Param("tenantId") Long tenantId, @Param("couponId") Long couponId);

    /** 编辑或删除券时移除其旧配对，两端同时失效。 */
    @Delete("DELETE FROM coupon_stack_pair WHERE tenant_id = #{tenantId} " +
            "AND (coupon_id_low = #{couponId} OR coupon_id_high = #{couponId})")
    int deleteByCouponId(@Param("tenantId") Long tenantId, @Param("couponId") Long couponId);

    /** ID 按大小存储，避免 A-B 与 B-A 重复。 */
    @Insert("INSERT INTO coupon_stack_pair (tenant_id, coupon_id_low, coupon_id_high) " +
            "VALUES (#{tenantId}, #{low}, #{high})")
    int insertPair(@Param("tenantId") Long tenantId, @Param("low") Long low, @Param("high") Long high);
}
