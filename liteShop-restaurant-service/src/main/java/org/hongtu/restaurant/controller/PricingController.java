package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.MemberDishPrice;
import org.hongtu.restaurant.enums.PricingEnums.CampaignType;
import org.hongtu.restaurant.VO.PricingCampaignSaveRequest;
import org.hongtu.restaurant.VO.PricingQuoteRequest;
import org.hongtu.restaurant.service.PricingService;
import org.springframework.web.bind.annotation.*;

/**
 * 定价活动、会员价和结算试价接口。
 */
@RestController
@RequestMapping("/dish/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final PricingService pricingService;

    /** 分页查询当前数据范围内的定价活动。 */
    @GetMapping("/campaign/page")
    @SaCheckPermission(value = {"restaurant:pricing"}, orRole = "SUPER_ADMIN")
    public Result<?> campaignPage(@RequestParam(defaultValue = "1") int currentPage,
                                  @RequestParam(defaultValue = "10") int pageSize,
                                  @RequestParam(required = false) String campaignName,
                                  @RequestParam(required = false) Integer campaignType,
                                  @RequestParam(required = false) Integer status) {
        return Result.success(pricingService.pageCampaigns(currentPage, pageSize, campaignName,
                CampaignType.fromCode(campaignType), status));
    }

    /** 查询定价活动完整配置。 */
    @GetMapping("/campaign/{id}")
    @SaCheckPermission(value = {"restaurant:pricing"}, orRole = "SUPER_ADMIN")
    public Result<?> campaignDetail(@PathVariable Long id) {
        return Result.success(pricingService.getCampaign(id));
    }

    /** 新增或更新定价活动。 */
    @PostMapping("/campaign")
    @SaCheckPermission(value = {"restaurant:pricing"}, orRole = "SUPER_ADMIN")
    public Result<?> saveCampaign(@RequestBody PricingCampaignSaveRequest request) {
        checkWritePermission(request.getId(), "restaurant:pricing:add", "restaurant:pricing:edit");
        return Result.success(pricingService.saveCampaign(request));
    }

    /** 逻辑删除定价活动。 */
    @DeleteMapping("/campaign/{id}")
    @SaCheckPermission(value = {"restaurant:pricing:delete"}, orRole = "SUPER_ADMIN")
    public Result<?> deleteCampaign(@PathVariable Long id) {
        pricingService.deleteCampaign(id);
        return Result.success(true);
    }

    /** 启用或停用定价活动。 */
    @PutMapping("/campaign/{id}/status")
    @SaCheckPermission(value = {"restaurant:pricing:edit"}, orRole = "SUPER_ADMIN")
    public Result<?> updateCampaignStatus(@PathVariable Long id, @RequestParam Integer status) {
        pricingService.updateCampaignStatus(id, status);
        return Result.success(true);
    }

    /** 分页查询常态会员菜品价。 */
    @GetMapping("/member-price/page")
    @SaCheckPermission(value = {"restaurant:pricing"}, orRole = "SUPER_ADMIN")
    public Result<?> memberPricePage(@RequestParam(defaultValue = "1") int currentPage,
                                     @RequestParam(defaultValue = "10") int pageSize,
                                     @RequestParam(required = false) Long storeId,
                                     @RequestParam(required = false) Long dishId,
                                     @RequestParam(required = false) Integer memberLevelCode) {
        return Result.success(pricingService.pageMemberPrices(currentPage, pageSize, storeId, dishId, memberLevelCode));
    }

    /** 按关键词加载少量可配置菜品，避免会员价弹窗扫描完整菜品列表。 */
    @GetMapping("/member-price/dish-options")
    @SaCheckPermission(value = {"restaurant:pricing"}, orRole = "SUPER_ADMIN")
    public Result<?> memberPriceDishOptions(@RequestParam(required = false) Long storeId,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long includeDishId,
                                            @RequestParam(defaultValue = "30") Integer limit) {
        return Result.success(pricingService.listMemberPriceDishOptions(
                storeId, keyword, includeDishId, limit));
    }

    /** 新增或更新常态会员菜品价。 */
    @PostMapping("/member-price")
    @SaCheckPermission(value = {"restaurant:pricing"}, orRole = "SUPER_ADMIN")
    public Result<?> saveMemberPrice(@RequestBody MemberDishPrice request) {
        checkWritePermission(request.getId(), "restaurant:pricing:add", "restaurant:pricing:edit");
        return Result.success(pricingService.saveMemberPrice(request));
    }

    /** 逻辑删除常态会员菜品价。 */
    @DeleteMapping("/member-price/{id}")
    @SaCheckPermission(value = {"restaurant:pricing:delete"}, orRole = "SUPER_ADMIN")
    public Result<?> deleteMemberPrice(@PathVariable Long id) {
        pricingService.deleteMemberPrice(id);
        return Result.success(true);
    }

    /** 启用或停用常态会员菜品价。 */
    @PutMapping("/member-price/{id}/status")
    @SaCheckPermission(value = {"restaurant:pricing:edit"}, orRole = "SUPER_ADMIN")
    public Result<?> updateMemberPriceStatus(@PathVariable Long id, @RequestParam Integer status) {
        pricingService.updateMemberPriceStatus(id, status);
        return Result.success(true);
    }

    /** 按服务端菜品、会员和活动配置计算购物车价格。 */
    @PostMapping("/quote")
    @SaCheckPermission(value = {"restaurant:pricing", "restaurant:dish", "restaurant:order:add",
            "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<?> quote(@RequestBody PricingQuoteRequest request) {
        return Result.success(pricingService.quote(request));
    }

    private void checkWritePermission(Long id, String addPermission, String editPermission) {
        if (StpUtil.hasRole("SUPER_ADMIN")) return;
        StpUtil.checkPermission(id == null ? addPermission : editPermission);
    }
}
