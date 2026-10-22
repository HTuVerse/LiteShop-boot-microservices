package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponMapper;
import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DAO.VerifyRecordMapper;
import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.DTO.VerifyRecord;
import org.hongtu.Result.Result;
import org.hongtu.service.CouponDishItemService;
import org.hongtu.service.UserCouponService;
import org.hongtu.service.MallCouponIssueService;
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
    private VerifyRecordMapper verifyRecordMapper;

    @Resource
    private CouponMapper couponMapper;

    @Resource
    private CouponDishItemService couponDishItemService;

    @Resource
    private MallCouponIssueService mallCouponIssueService;

    /** 积分兑换按订单幂等发券。 */
    @PostMapping("/mall-issue")
    public Result<Long> issueMallCoupon(@RequestParam("userId") Long userId,
                                         @RequestParam("couponId") Long couponId,
                                         @RequestParam("orderId") Long orderId) {
        return Result.success(mallCouponIssueService.issue(userId, couponId, orderId));
    }

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

    /** 返回指定订单的券核销结果，供跨服务事务回滚后对账。 */
    @GetMapping("/verification-state")
    public Result<String> verificationState(@RequestParam Long userCouponId, @RequestParam Long orderId) {
        VerifyRecord record = verifyRecordMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VerifyRecord>()
                .eq(VerifyRecord::getUserCouponId, userCouponId)
                .eq(VerifyRecord::getOrderId, orderId)
                .last("LIMIT 1"));
        if (record == null) return Result.success("ABSENT");
        if (record.getStatus() == org.hongtu.enums.VerifyStatusEnum.VERIFYED) return Result.success("ACTIVE");
        if (record.getStatus() == org.hongtu.enums.VerifyStatusEnum.NOT_VERIFY) return Result.success("RESTORED");
        throw new IllegalStateException("优惠券核销记录状态无效");
    }

    /** 恢复回滚前的订单核销关系。 */
    @PostMapping("/undo-restore")
    public Result<Void> undoRestore(@RequestParam Long userCouponId, @RequestParam Long orderId) {
        userCouponService.undoRestoreOnRollback(userCouponId, orderId);
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
