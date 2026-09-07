package org.hongtu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.Coupon;
import org.hongtu.Result.Result;
import org.hongtu.enums.OnOrOffShelfEnum;

/**
 * <p>
 * 优惠券 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
public interface CouponService extends IService<Coupon> {

    /**
     * 分页查询优惠券列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param name 优惠券名称（模糊查询）
     * @param status 状态（可选）
     * @return 分页结果
     */
    Result<Page<Coupon>> getCouponList(Integer currentPage, Integer pageSize, String name, OnOrOffShelfEnum status);

    /**
     * 创建优惠券
     * @param coupon 优惠券信息
     * @return 创建结果
     */
    Result<Coupon> createCoupon(Coupon coupon);

    /**
     * 更新优惠券
     * @param coupon 优惠券信息
     * @return 更新结果
     */
    Result<Coupon> updateCoupon(Coupon coupon);

    /**
     * 删除优惠券
     * @param id 优惠券ID
     * @return 删除结果
     */
    Result deleteCoupon(Long id);

    /**
     * 切换优惠券上下架状态（自动取反）
     * @param id 优惠券ID
     * @return 操作结果
     */
    Result toggleCouponStatus(Long id);

    /**
     * 根据ID获取优惠券详情
     * @param id 优惠券ID
     * @return 优惠券详情
     */
    Result<Coupon> getCouponById(Long id);

    /**
     * 批量下架过期优惠券（定时任务调用）
     * @return 下架数量
     */
    int batchOffShelfExpiredCoupons();

}
