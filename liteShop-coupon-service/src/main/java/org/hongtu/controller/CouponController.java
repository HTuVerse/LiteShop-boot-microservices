package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.CouponStockAdjustment;
import org.hongtu.DTO.CouponStockAdjustmentRequest;
import org.hongtu.Result.Result;
import org.hongtu.enums.OnOrOffShelfEnum;
import org.hongtu.service.CouponService;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 优惠券 前端控制器
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@RestController
@RequestMapping("/coupon")
public class CouponController {

    @Resource
    private CouponService couponService;

    /**
     * 分页查询优惠券列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param name 优惠券名称（模糊查询）
     * @param status 状态：0-下架，1-上架
     * @return 分页结果
     */
    @SaCheckPermission(value = {"marketing:coupon"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    @GetMapping("/list")
    public Result<Page<Coupon>> getCouponList(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        
            OnOrOffShelfEnum statusEnum = status != null ? OnOrOffShelfEnum.getByCode(status) : null;
            return couponService.getCouponList(currentPage, pageSize, name, statusEnum);
        
    }

    /**
     * 获取优惠券详情
     * @param id 优惠券ID
     * @return 优惠券详情
     */
    @SaCheckPermission(value = {"marketing:coupon"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    @GetMapping("/detail")
    public Result<Coupon> getCouponById(@RequestParam Long id) {
        
            return couponService.getCouponById(id);
        
    }

    /**
     * 创建优惠券
     * @param coupon 优惠券信息
     * @return 创建结果
     */
    @PostMapping
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Coupon> createCoupon(@RequestBody Coupon coupon) {
        
            return couponService.createCoupon(coupon);
        
    }

    /**
     * 更新优惠券
     * @param coupon 优惠券信息
     * @return 更新结果
     */
    @PutMapping
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Coupon> updateCoupon(@RequestBody Coupon coupon) {
        
            return couponService.updateCoupon(coupon);
        
    }

    /** 按增减量调整剩余可发库存。 */
    @PutMapping("/stock")
    @SaCheckPermission(value = {"marketing:coupon:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Coupon> adjustStock(@RequestBody CouponStockAdjustmentRequest request) {
        if (request == null) return Result.error("库存调整参数不能为空");
        return couponService.adjustStock(request.getCouponId(), request.getDelta(),
                request.getReason(), StpUtil.getLoginIdAsLong());
    }

    /** 查询指定优惠券的库存调整记录。 */
    @GetMapping("/stock/adjustments")
    @SaCheckPermission(value = {"marketing:coupon"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<CouponStockAdjustment>> getStockAdjustments(@RequestParam Long couponId,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return couponService.getStockAdjustments(couponId, currentPage, pageSize);
    }

    /**
     * 删除优惠券
     * @param id 优惠券ID
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteCoupon(@RequestParam Long id) {
        
            return couponService.deleteCoupon(id);
        
    }

    /**
     * 切换优惠券上下架状态
     * 如果当前是上架状态则下架，如果当前是下架状态则上架
     * @param id 优惠券ID
     * @return 操作结果
     */
    @PutMapping("/toggle-status")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result toggleStatus(@RequestParam Long id) {
        
            return couponService.toggleCouponStatus(id);
        
    }

}
