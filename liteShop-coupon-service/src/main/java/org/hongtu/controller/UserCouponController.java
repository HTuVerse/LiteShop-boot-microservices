package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.service.UserCouponService;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 用户优惠券关联表 前端控制器
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@RestController
@RequestMapping("/user-coupon")
public class UserCouponController {

    @Resource
    UserCouponService userCouponService;

    /**
     * 获取当前用户的优惠券列表
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @param searchKey 搜索关键字
     * @param status 优惠券状态（可选）：0-未生效，1-未使用，2-已使用，3-已过期，null-全部
     * @return 优惠券列表
     */
    @GetMapping("/list")
    public Result getUserCoupons(@RequestParam(defaultValue = "1") Integer currentPage,
                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                 @RequestParam(defaultValue = "最近领取") String searchKey,
                                 @RequestParam(required = false) Integer status) {
        
            // 获取当前登录用户ID
            Long userId = StpUtil.getLoginIdAsLong();
            // 获取用户优惠券列表
            return userCouponService.getUserCoupons(currentPage, pageSize, searchKey, userId, CouponStatusEnum.getByCode(status));
        
    }

    /**
     * 发送优惠券给用户（管理员接口）
     * @param userId 用户ID
     * @param couponId 优惠券ID
     * @param startTime 生效时间
     * @param expiredTime 过期时间
     * @return 操作结果
     */
    @PostMapping("/send")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:send"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result sendCouponToUser(@RequestParam Long userId,
                                   @RequestParam Long couponId,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date expiredTime) {
        return userCouponService.sendCouponToUser(userId, couponId, startTime, expiredTime);
    }


}
