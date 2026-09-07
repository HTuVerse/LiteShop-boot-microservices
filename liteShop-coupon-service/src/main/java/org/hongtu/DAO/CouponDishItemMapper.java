package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.DTO.CouponDishItem;

import java.util.List;

/**
 * <p>
 * 优惠券-菜品关联表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2026-02-07
 */
@Mapper
public interface CouponDishItemMapper extends BaseMapper<CouponDishItem> {

    /**
     * 根据优惠券ID查询关联的菜品列表
     *
     * @param couponId 优惠券ID
     * @return 关联的菜品列表
     */
    @Select("SELECT * FROM coupon_dish_item WHERE coupon_id = #{couponId}")
    List<CouponDishItem> selectByCouponId(@Param("couponId") Long couponId);

    /**
     * 根据优惠券ID删除所有关联菜品
     *
     * @param couponId 优惠券ID
     * @return 删除数量
     */
    @Delete("DELETE FROM coupon_dish_item WHERE coupon_id = #{couponId}")
    int deleteByCouponId(@Param("couponId") Long couponId);
}
