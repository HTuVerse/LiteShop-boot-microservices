package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.VO.StoreDishConfigRequest;
import org.hongtu.restaurant.VO.MenuChangeSubmitVO;
import org.hongtu.restaurant.service.DishService;
import org.hongtu.restaurant.service.MenuChangeService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品表 前端控制器
 * @author Ht
 * @since 2025-09-19
 */
@RestController
@RequestMapping("/dish")
public class DishController {

    @Resource
    DishService dishService;

    @Resource
    MenuChangeService menuChangeService;

    /**
     * 根据ID查询菜品详情
     * @param id 菜品ID
     * @return 菜品详情
     */
    @GetMapping("/detail/{id}")
    public Result<DishVO> getDishDetail(@PathVariable Long id,
                                       @RequestParam(required = false) Long storeId) {
        DishVO dishVO = storeId == null
                ? dishService.getDishDetail(id)
                : dishService.getEffectiveDish(id, storeId, false);
        if (dishVO != null) {
            return Result.success("查询菜品详情成功", dishVO);
        } else {
            return Result.error("查询菜品详情失败");
        }
    }

    /**
     * 添加菜品
     * @param dish 菜品信息
     * @return 结果
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:dish:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addDish(@RequestBody Dish dish) {
        boolean result = dishService.addDish(dish);
        if (result) {
            return Result.success("添加菜品成功", result);
        } else {
            return Result.error("添加菜品失败");
        }
    }

    /**
     * 更新菜品信息
     * @param dish 菜品信息
     * @return 结果
     */
    @PutMapping("/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:dish:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateDish(@RequestBody Dish dish) {
        boolean result = dishService.updateDish(dish);
        if (result) {
            return Result.success("更新菜品成功", result);
        } else {
            return Result.error("更新菜品失败");
        }
    }

    /**
     * 删除菜品
     * @param id 菜品ID
     * @return 结果
     */
    @DeleteMapping("/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:dish:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteDish(@PathVariable Long id) {
        boolean result = dishService.deleteDish(id);
        if (result) {
            return Result.success("删除菜品成功", result);
        } else {
            return Result.error("删除菜品失败");
        }
    }

    /**
     * 批量删除菜品
     * @param ids 菜品ID列表
     * @return 结果
     */
    @DeleteMapping("/batchDelete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:dish:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> batchDeleteDish(@RequestBody List<Long> ids) {
        boolean result = dishService.batchDeleteDish(ids);
        if (result) {
            return Result.success("批量删除菜品成功", result);
        } else {
            return Result.error("批量删除菜品失败");
        }
    }


    /**
     * 分页查询菜品列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID
     * @param categoryId 分类ID
     * @param dishName 菜品名称
     * @param isRecommend 是否推荐
     * @param isHot 是否热门
     * @param isNew 是否新品
     * @param saleStatus 销售状态
     * @return 分页菜品列表
     */
    @GetMapping("/pageList")
    @SaCheckPermission(value = {"restaurant:dish", "marketing:coupon"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result getDishPage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String dishName,
            @RequestParam(required = false) Integer isRecommend,
            @RequestParam(required = false) Integer isHot,
            @RequestParam(required = false) Integer isNew,
            @RequestParam(required = false) Integer saleStatus,
            @RequestParam(required = false) Integer sceneType,
            @RequestParam(required = false) Integer sourceType
    ) {

        // 封装查询参数
        Map<String, Object> params = new HashMap<>();
        params.put("storeId", storeId);
        params.put("categoryId", categoryId);
        params.put("dishName", dishName);
        params.put("isRecommend", isRecommend);
        params.put("isHot", isHot);
        params.put("isNew", isNew);
        params.put("saleStatus", saleStatus);
        params.put("sceneType", sceneType);
        params.put("sourceType", sourceType);

        // 创建分页对象
        Page<Dish> page = new Page<>(currentPage, pageSize);

        // 分页查询
        Page<DishVO> dishPage = dishService.getDishPage(params, page);

        return Result.success("查询菜品列表成功", dishPage);
    }

    /**
     * 不分页查询菜品列表
     * @param searchKey 搜索关键字（菜品名称）
     * @param isListBySort 是否按分类查询：0-直接返回菜品列表，1-按分类分组返回
     * @param isRecommend 是否推荐：1-是，0-否
     * @param isHot 是否热门：1-是，0-否
     * @param isNew 是否新品：1-是，0-否
     * @return 菜品列表或按分类分组的菜品列表
     */
    @GetMapping("/listDishNoPage")
    @SaIgnore
    public Result listDishNoPage(@RequestParam(required = false, defaultValue = "") String searchKey,
                                    @RequestParam(required = false, defaultValue = "0") Integer isListBySort,
                                    @RequestParam(required = false) Integer isRecommend,
                                    @RequestParam(required = false) Integer isHot,
                                    @RequestParam(required = false) Integer isNew,
                                    @RequestParam(required = false) Long storeId){
       return dishService.listDishNoPage(storeId, searchKey, isListBySort, isRecommend, isHot, isNew);
    }

    /**
     * 更新总部菜品在指定门店的价格、库存和上下架状态。
     */
    @PutMapping("/store-config")
    @SaCheckPermission(value = {"restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<MenuChangeSubmitVO> updateStoreConfig(@RequestBody StoreDishConfigRequest request) {
        return Result.success("门店菜品变更已提交",
                menuChangeService.submit(request, StpUtil.getLoginIdAsLong()));
    }


/*    *//**
     * 更新菜品销量
     * @param id 菜品ID
     * @param count 增加的销量
     * @return 结果
     *//*
    @PutMapping("/updateSalesCount")
    public Result<Boolean> updateDishSalesCount(@RequestParam Long id, @RequestParam Integer count) {
        boolean result = dishService.updateDishSalesCount(id, count);
        if (result) {
            return Result.success("更新菜品销量成功", result);
        } else {
            return Result.error("更新菜品销量失败");
        }
    }*/

}
