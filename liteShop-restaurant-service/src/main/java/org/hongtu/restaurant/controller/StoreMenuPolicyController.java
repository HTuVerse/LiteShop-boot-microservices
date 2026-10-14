package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.StoreMenuPolicy;
import org.hongtu.restaurant.service.StoreMenuPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店菜单策略接口。
 */
@RestController
@RequestMapping("/dish/store-policy")
public class StoreMenuPolicyController {

    @Resource
    private StoreMenuPolicyService policyService;

    /** 获取指定门店的有效菜单策略。 */
    @GetMapping
    @SaCheckPermission(value = {"restaurant:store", "restaurant:store:edit",
            "restaurant:dish", "restaurant:dish:edit", "restaurant:category",
            "restaurant:category:add", "restaurant:category:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<StoreMenuPolicy> getPolicy(@RequestParam Long storeId) {
        return Result.success(policyService.getEffectivePolicy(storeId));
    }

    /** 由总部保存指定门店的菜单策略。 */
    @PutMapping
    @SaCheckPermission(value = {"restaurant:store:edit", "restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<StoreMenuPolicy> savePolicy(@RequestBody StoreMenuPolicy policy) {
        return Result.success("保存门店菜单策略成功", policyService.savePolicy(policy));
    }
}
