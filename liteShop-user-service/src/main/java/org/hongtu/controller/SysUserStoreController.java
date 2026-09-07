package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.service.SysUserStoreService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sys-user-store")
public class SysUserStoreController {

    @Resource
    private SysUserStoreService userStoreService;

    @GetMapping("/page")
    @SaCheckPermission(value = "system:user-store", orRole = "SUPER_ADMIN")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<org.hongtu.VO.UserStoreAuthorizationVO>> page(
            @RequestParam(defaultValue = "1") int currentPage, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Boolean authorized) {
        return Result.success(userStoreService.authorizationPage(currentPage, pageSize, keyword, storeId, authorized));
    }

    @GetMapping("/{userId}")
    @SaCheckPermission(value = {"system:user-store"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<Long>> getStores(@PathVariable Long userId) {
        return Result.success(userStoreService.getStoreIds(userId));
    }

    @PostMapping("/assign")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user-store:assign"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Void> assignStores(@RequestParam Long userId,
                                     @RequestParam(required = false) Long defaultStoreId,
                                     @RequestBody List<Long> storeIds) {
        return userStoreService.assignStores(userId, storeIds, defaultStoreId);
    }
}
