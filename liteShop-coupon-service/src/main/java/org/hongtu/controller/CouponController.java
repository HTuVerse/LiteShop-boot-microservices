package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.Coupon;
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
        try {
            OnOrOffShelfEnum statusEnum = status != null ? OnOrOffShelfEnum.getByCode(status) : null;
            return couponService.getCouponList(currentPage, pageSize, name, statusEnum);
        } catch (Exception e) {
            log.error("查询优惠券列表异常", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取优惠券详情
     * @param id 优惠券ID
     * @return 优惠券详情
     */
    @SaCheckPermission(value = {"marketing:coupon"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    @GetMapping("/detail")
    public Result<Coupon> getCouponById(@RequestParam Long id) {
        try {
            return couponService.getCouponById(id);
        } catch (Exception e) {
            log.error("获取优惠券详情异常：id={}", id, e);
            return Result.error("获取详情失败：" + e.getMessage());
        }
    }

    /**
     * 创建优惠券
     * @param coupon 优惠券信息
     * @return 创建结果
     */
    @PostMapping
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Coupon> createCoupon(@RequestBody Coupon coupon) {
        try {
            return couponService.createCoupon(coupon);
        } catch (Exception e) {
            log.error("创建优惠券异常", e);
            return Result.error("创建失败：" + e.getMessage());
        }
    }

    /**
     * 更新优惠券
     * @param coupon 优惠券信息
     * @return 更新结果
     */
    @PutMapping
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Coupon> updateCoupon(@RequestBody Coupon coupon) {
        try {
            return couponService.updateCoupon(coupon);
        } catch (Exception e) {
            log.error("更新优惠券异常：id={}", coupon != null ? coupon.getId() : null, e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    /**
     * 删除优惠券
     * @param id 优惠券ID
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteCoupon(@RequestParam Long id) {
        try {
            return couponService.deleteCoupon(id);
        } catch (Exception e) {
            log.error("删除优惠券异常：id={}", id, e);
            return Result.error("删除失败：" + e.getMessage());
        }
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
        try {
            return couponService.toggleCouponStatus(id);
        } catch (Exception e) {
            log.error("切换优惠券状态异常：id={}", id, e);
            return Result.error("操作失败：" + e.getMessage());
        }
    }

}
