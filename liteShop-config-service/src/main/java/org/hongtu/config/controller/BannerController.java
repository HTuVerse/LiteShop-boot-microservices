package org.hongtu.config.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.config.DTO.Banner;
import org.hongtu.config.VO.BannerVO;
import org.hongtu.config.enums.BannerPositionEnum;
import org.hongtu.config.service.BannerService;
import org.hongtu.enums.BooleanEnum;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 轮播图配置 Controller
 *
 * @author Ht
 */
@RestController
@RequestMapping("/config/banner")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    // ==================== 小程序端接口（公开） ====================

    /**
     * 获取首页轮播图
     */
    @GetMapping("/home")
    @SaIgnore
    public Result<List<BannerVO>> getHomeBanners() {
        List<Banner> banners = bannerService.getHomeBanners();
        return Result.success(convertToVOList(banners));
    }

    /**
     * 获取点餐页轮播图
     */
    @GetMapping("/order")
    @SaIgnore
    public Result<List<BannerVO>> getOrderBanners() {
        List<Banner> banners = bannerService.getOrderBanners();
        return Result.success(convertToVOList(banners));
    }

    /**
     * 根据位置获取轮播图
     */
    @GetMapping("/position/{position}")
    @SaIgnore
    public Result<List<BannerVO>> getBannersByPosition(@PathVariable Integer position) {
        BannerPositionEnum positionEnum = BannerPositionEnum.fromCode(position);
        if (positionEnum == null) {
            return Result.error("无效的轮播图位置");
        }
        List<Banner> banners = bannerService.getEnabledBannersByPosition(positionEnum);
        return Result.success(convertToVOList(banners));
    }

    // ==================== 管理端接口（需要认证） ====================

    /**
     * 获取所有轮播图列表（管理端）
     */
    @GetMapping("/admin/list")
    @SaCheckPermission(value = {"config:banner"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<Banner>> getAllBanners(
            @RequestParam(required = false) Integer position,
            @RequestParam(required = false) Integer status) {

        List<Banner> banners = bannerService.lambdaQuery()
                .eq(position != null, Banner::getPosition, BannerPositionEnum.fromCode(position))
                .eq(status != null, Banner::getStatus, BooleanEnum.fromCode(status))
                .orderByAsc(Banner::getPosition)
                .orderByAsc(Banner::getSortOrder)
                .list();
        return Result.success(banners);
    }

    /**
     * 获取轮播图详情
     */
    @GetMapping("/admin/{id}")
    @SaCheckPermission(value = {"config:banner"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Banner> getBannerById(@PathVariable Long id) {
        Banner banner = bannerService.getById(id);
        if (banner == null) {
            return Result.error("轮播图不存在");
        }
        return Result.success(banner);
    }

    /**
     * 新增轮播图
     */
    @PostMapping("/admin/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:banner:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addBanner(@RequestBody Banner banner) {
        banner.setCreatedAt(LocalDateTime.now());
        banner.setUpdatedAt(LocalDateTime.now());
        if (banner.getSortOrder() == null) {
            banner.setSortOrder(0);
        }
        if (banner.getStatus() == null) {
            banner.setStatus(BooleanEnum.YES);
        }
        if (banner.getLinkType() == null) {
            banner.setLinkType(3); // 默认不跳转
        }
        boolean success = bannerService.save(banner);
        return success ? Result.success(true) : Result.error("新增失败");
    }

    /**
     * 更新轮播图
     */
    @PutMapping("/admin/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:banner:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateBanner(@RequestBody Banner banner) {
        if (banner.getId() == null) {
            return Result.error("轮播图ID不能为空");
        }
        Banner existing = bannerService.getById(banner.getId());
        if (existing == null) return Result.error("轮播图不存在或无权访问");
        org.hongtu.security.OperationPermission.requireChange("config:banner:status", existing.getStatus(), banner.getStatus());
        banner.setUpdatedAt(LocalDateTime.now());
        boolean success = bannerService.updateById(banner);
        return success ? Result.success(true) : Result.error("更新失败");
    }

    /**
     * 删除轮播图
     */
    @DeleteMapping("/admin/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:banner:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteBanner(@PathVariable Long id) {
        boolean success = bannerService.removeById(id);
        return success ? Result.success(true) : Result.error("删除失败");
    }

    /**
     * 更新轮播图状态
     */
    @PutMapping("/admin/status/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:banner:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        boolean success = bannerService.updateStatus(id, status);
        return success ? Result.success(true) : Result.error("更新状态失败");
    }

    /**
     * 更新轮播图排序
     */
    @PutMapping("/admin/sort/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:banner:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateSortOrder(@PathVariable Long id, @RequestParam Integer sortOrder) {
        boolean success = bannerService.updateSortOrder(id, sortOrder);
        return success ? Result.success(true) : Result.error("更新排序失败");
    }

    /**
     * 批量删除轮播图
     */
    @DeleteMapping("/admin/batch-delete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"config:banner:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> batchDelete(@RequestBody List<Long> ids) {
        boolean success = bannerService.removeByIds(ids);
        return success ? Result.success(true) : Result.error("批量删除失败");
    }

    /**
     * 转换为VO列表
     */
    private List<BannerVO> convertToVOList(List<Banner> banners) {
        return banners.stream().map(banner -> {
            BannerVO vo = new BannerVO();
            BeanUtils.copyProperties(banner, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
