package org.hongtu.api.client;

import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.Result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 优惠券服务 Feign 客户端
 * 对应 coupon-service 的 /inner/coupon/** 内部接口
 */
@FeignClient(name = "liteShop-coupon-service", contextId = "couponFeignClient")
public interface CouponFeignClient {

    /**
     * 下单核销用户优惠券（替代原 restaurant 直接调 UserCouponService.verifyCouponOnOrder）
     */
    @PostMapping("/inner/coupon/verify")
    Result<Void> verifyCouponOnOrder(@RequestParam("userCouponId") Long userCouponId,
                                     @RequestParam("orderId") Long orderId);

    /**
     * 取消订单回滚优惠券（替代原 UserCouponService.restoreCouponOnCancel）
     */
    @PostMapping("/inner/coupon/restore")
    Result<Void> restoreCouponOnCancel(@RequestParam("userCouponId") Long userCouponId,
                                       @RequestParam("orderId") Long orderId);

    /**
     * 校验多张券的叠加/互斥规则（替代原 UserCouponService.validateCouponStackRules）
     */
    @PostMapping("/inner/coupon/validate-stack")
    Result<Void> validateCouponStackRules(@RequestBody List<Long> userCouponIds);

    /**
     * 批量查询用户优惠券
     */
    @PostMapping("/inner/coupon/user-coupons/batch")
    Result<List<UserCoupon>> getUserCouponsByIds(@RequestBody List<Long> userCouponIds);

    /**
     * 查询优惠券模板（替代原 CouponMapper.selectById）
     */
    @GetMapping("/inner/coupon/{id}")
    Result<Coupon> getCouponById(@PathVariable("id") Long id);

    /**
     * 批量查询优惠券模板
     */
    @PostMapping("/inner/coupon/batch")
    Result<List<Coupon>> getCouponsByIds(@RequestBody List<Long> couponIds);

    /**
     * 查询菜品免单券关联的菜品明细（替代原 CouponDishItemService.getItemsByCouponId）
     */
    @GetMapping("/inner/coupon/dish-items")
    Result<List<CouponDishItem>> getItemsByCouponId(@RequestParam("couponId") Long couponId);

    /** 按兑换订单幂等发券，返回用户券ID。 */
    @PostMapping("/inner/coupon/mall-issue")
    Result<Long> issueMallCoupon(@RequestParam("userId") Long userId,
                                 @RequestParam("couponId") Long couponId,
                                 @RequestParam("orderId") Long orderId);
}
