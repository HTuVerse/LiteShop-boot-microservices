package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponMapper;
import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.Result.Result;
import org.hongtu.service.CouponDishItemService;
import org.hongtu.service.UserCouponService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 优惠券服务内部接口（仅供其他微服务通过 Feign 调用）
 * 替代原单体中 restaurant 模块对 UserCouponService / UserCouponMapper / CouponMapper / CouponDishItemService 的直接注入
 * 外部经网关访问 /inner/** 会被全局过滤器拦截
 */
@Slf4j
@RestController
@RequestMapping("/inner/coupon")
@SaIgnore
public class InnerCouponController {

    @Resource
    private UserCouponService userCouponService;

    @Resource
    private UserCouponMapper userCouponMapper;

    @Resource
    private CouponMapper couponMapper;

    @Resource
    private CouponDishItemService couponDishItemService;

    /**
     * 下单核销用户优惠券（失败抛异常，由全局异常处理器返回错误 Result）
     */
    @PostMapping("/verify")
    public Result<Void> verifyCouponOnOrder(@RequestParam("userCouponId") Long userCouponId,
                                            @RequestParam("orderId") Long orderId) {
        userCouponService.verifyCouponOnOrder(userCouponId, orderId);
        return Result.success();
    }

    /**
     * 取消订单回滚优惠券
     */
    @PostMapping("/restore")
    public Result<Void> restoreCouponOnCancel(@RequestParam("userCouponId") Long userCouponId,
                                              @RequestParam("orderId") Long orderId) {
        userCouponService.restoreCouponOnCancel(userCouponId, orderId);
        return Result.success();
    }

    /**
     * 校验多张券的叠加/互斥规则
     */
    @PostMapping("/validate-stack")
    public Result<Void> validateCouponStackRules(@RequestBody List<Long> userCouponIds) {
        userCouponService.validateCouponStackRules(userCouponIds);
        return Result.success();
    }

    /**
     * 批量查询用户优惠券
     */
    @PostMapping("/user-coupons/batch")
    public Result<List<UserCoupon>> getUserCouponsByIds(@RequestBody List<Long> userCouponIds) {
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        return Result.success(userCouponMapper.selectByIds(userCouponIds));
    }

    /**
     * 查询优惠券模板
     */
    @GetMapping("/{id}")
    public Result<Coupon> getCouponById(@PathVariable("id") Long id) {
        return Result.success(couponMapper.selectById(id));
    }

    /**
     * 批量查询优惠券模板
     */
    @PostMapping("/batch")
    public Result<List<Coupon>> getCouponsByIds(@RequestBody List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        return Result.success(couponMapper.selectByIds(couponIds));
    }

    /**
     * 查询菜品免单券关联的菜品明细
     */
    @GetMapping("/dish-items")
    public Result<List<CouponDishItem>> getItemsByCouponId(@RequestParam("couponId") Long couponId) {
        return Result.success(couponDishItemService.getItemsByCouponId(couponId));
    }
}
