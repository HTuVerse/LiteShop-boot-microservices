package org.hongtu.restaurant.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DTO.DishCategory;
import org.hongtu.restaurant.service.DishCategoryService;
import org.hongtu.restaurant.service.DishService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品分类表 前端控制器
 *
 * @author Ht
 * @since 2025-09-19
 */
@RestController
@RequestMapping("/dish/category")
public class DishCategoryController {

    @Resource
    DishCategoryService dishCategoryService;

    @Resource
    DishService dishService;

    @Resource
    StoreDataScopeService storeDataScopeService;

    /**
     * 添加分类
     * @param category 分类信息
     * @return 结果
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:category:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addCategory(@RequestBody DishCategory category) {
        storeDataScopeService.checkStoreAccess(category.getStoreId());
        // 设置初始值
        boolean result = dishCategoryService.save(category);
        if (result) {
            return Result.success("添加分类成功", result);
        } else {
            return Result.error("添加分类失败");
        }
    }

    /**
     * 更新分类
     * @param category 分类信息
     * @return 结果
     */
    @PutMapping("/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:category:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateCategory(@RequestBody DishCategory category) {
        storeDataScopeService.checkStoreAccess(category.getStoreId());
        category.setUpdatedAt(LocalDateTime.now());
        boolean result = dishCategoryService.updateById(category);
        if (result) {
            return Result.success("更新分类成功", result);
        } else {
            return Result.error("更新分类失败");
        }
    }

    /**
     * 删除分类
     * @param id 分类ID
     * @return 结果
     */
    @DeleteMapping("/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:category:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteCategory(@PathVariable Long id) {

        // 检查该分类下是否存在菜品
        LambdaQueryWrapper<Dish> dishQueryWrapper = new LambdaQueryWrapper<>();
        dishQueryWrapper.eq(Dish::getCategoryId, id);
        int dishCount = (int) dishService.count(dishQueryWrapper);
        if (dishCount > 0) {
            return Result.error("该分类下存在菜品，请先删除菜品");
        }
        boolean result = dishCategoryService.removeById(id);
        if (result) {
            return Result.success("删除分类成功", result);
        } else {
            return Result.error("删除分类失败");
        }
    }

    /**
     * 根据ID查询分类
     * @param id 分类ID
     * @return 分类信息
     */
    @GetMapping("/getCategoryDetail")
    @SaCheckPermission(value = {"restaurant:category"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<DishCategory> getCategoryById(@RequestParam Long id) {
        DishCategory category = dishCategoryService.getById(id);
        if (category != null) {
            return Result.success("查询分类成功", category);
        } else {
            return Result.error("查询分类失败");
        }
    }

    /**
     * 分页查询分类列表
     * @param currentPage 当前页，默认1
     * @param pageSize 每页条数，默认10
     * @param searchKey 搜索关键字（分类名称模糊）
     * @param isShow 是否显示：0-隐藏，1-显示，不传查全部
     * @param level 分类层级：1-一级，2-二级，不传查全部
     * @return 分页结果
     */
    @GetMapping("/pageList")
    @SaCheckPermission(value = {"restaurant:category", "restaurant:dish", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<DishCategory>> getCategoryPage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) Integer isShow,
            @RequestParam(required = false) Integer level
    ) {
        Page<DishCategory> page = dishCategoryService.getCategoryPage(currentPage, pageSize, searchKey, isShow, level);
        return Result.success("查询分类列表成功", page);
    }
}
