package org.hongtu.restaurant.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.service.StoreInfoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 门店信息表 前端控制器
 *
 * @author Ht
 * @since 2025-09-19
 */
@RestController
@RequestMapping("/store-info")
public class StoreInfoController {

    @Resource
    private StoreInfoService storeInfoService;

    /**
     * 根据ID查询门店详情
     * @param id 门店ID
     * @return 门店信息
     */
    @GetMapping("/getStoreDetail")
    @SaIgnore
    public Result<StoreInfo> getStoreById(@RequestParam Long id) {
        
            StoreInfo storeInfo = storeInfoService.getById(id);
            if (storeInfo == null) {
                return Result.error("门店不存在");
            }
            return Result.success("查询门店详情成功", storeInfo);
        
    }

    /**
     * 分页查询门店列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeName 门店名称（模糊查询）
     * @param status 门店状态：0-停业，1-营业,2-休息中
     * @return 分页门店列表
     */
    @GetMapping("/pageList")
    @SaCheckPermission(value = {"restaurant:store", "restaurant:category", "restaurant:dish", "restaurant:table", "restaurant:order", "restaurant:printer", "restaurant:analysis", "system:user-store", "operations:direct-cashier", "operations:table-cashier", "operations:console", "operations:templates", "marketing:coupon"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<StoreInfo>> getStorePage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) Integer status
    ) {
        
            Page<StoreInfo> result = storeInfoService.getStorePage(currentPage, pageSize, storeName, status);
            return Result.success("查询门店列表成功", result);
        
    }

    /**
     * 新增门店
     * @param storeInfo 门店信息
     * @return 新增结果
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:store:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<StoreInfo> addStore(@RequestBody StoreInfo storeInfo) {
        
            boolean result = storeInfoService.addStore(storeInfo);
            if (result) {
                return Result.success("新增门店成功", storeInfo);
            }
            return Result.error("新增门店失败");
        
    }

    /**
     * 更新门店信息
     * @param storeInfo 门店信息
     * @return 更新结果
     */
    @PutMapping("/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:store:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<StoreInfo> updateStore(@RequestBody StoreInfo storeInfo) {
        
            if (storeInfo.getId() == null) {
                return Result.error("门店ID不能为空");
            }
            boolean result = storeInfoService.updateStore(storeInfo);
            if (result) {
                return Result.success("更新门店成功", storeInfo);
            }
            return Result.error("更新门店失败");
        
    }

    /**
     * 删除门店
     * @param id 门店ID
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:store:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteStore(@RequestParam Long id) {
        
            boolean result = storeInfoService.deleteStore(id);
            if (result) {
                return Result.success(200, "删除门店成功");
            }
            return Result.error("删除门店失败");
        
    }

    /**
     * 批量删除门店
     * @param ids 门店ID列表
     * @return 删除结果
     */
    @DeleteMapping("/deleteBatch")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:store:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteStoresBatch(@RequestBody List<Long> ids) {
        
            if (ids == null || ids.isEmpty()) {
                return Result.error("门店ID列表不能为空");
            }
            boolean result = storeInfoService.deleteStoresBatch(ids);
            if (result) {
                return Result.success("批量删除门店成功", null);
            }
            return Result.error("批量删除门店失败");
        
    }

    /**
     * 修改门店状态
     * @param id 门店ID
     * @param status 状态：0-停业，1-营业
     * @return 修改结果
     */
    @PutMapping("/updateStatus")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:store:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result updateStoreStatus(@RequestParam Long id, @RequestParam Integer status) {
        
            if (status == null || (status != 0 && status != 1)) {
                return Result.error("状态值无效，只能是0或1");
            }
            boolean result = storeInfoService.updateStoreStatus(id, status);
            if (result) {
                return Result.success(200, "修改门店状态成功");
            }
            return Result.error("修改门店状态失败");
        
    }

}
