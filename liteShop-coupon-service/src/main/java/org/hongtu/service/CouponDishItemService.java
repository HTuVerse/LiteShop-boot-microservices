package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.CouponDishItem;

import java.util.List;

/**
 * <p>
 * 优惠券-菜品关联表 服务类
 * </p>
 *
 * @author Ht
 * @since 2026-02-07
 */
public interface CouponDishItemService extends IService<CouponDishItem> {

    /**
     * 根据优惠券ID查询关联的菜品列表
     *
     * @param couponId 优惠券ID
     * @return 关联的菜品列表
     */
    List<CouponDishItem> getItemsByCouponId(Long couponId);

    /**
     * 保存优惠券关联菜品（先删后增）
     *
     * @param couponId 优惠券ID
     * @param items    菜品列表
     */
    void saveItems(Long couponId, List<CouponDishItem> items);

    /**
     * 删除优惠券关联的所有菜品
     *
     * @param couponId 优惠券ID
     */
    void deleteItemsByCouponId(Long couponId);
}
