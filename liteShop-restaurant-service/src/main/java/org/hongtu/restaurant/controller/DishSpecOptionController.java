package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.VO.DishSpecOptionRequest;
import org.hongtu.restaurant.VO.SpecValue;
import org.hongtu.restaurant.service.DishSpecOptionService;
import org.hongtu.restaurant.service.DishService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.utils.SpecValueConverter;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.hongtu.api.dto.ResourceBinding;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.hongtu.enums.StatusEnum;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品规格选项表 前端控制器
 * @author Ht
 * @since 2025-09-19
 */
@RestController
@RequestMapping("/dish/spec")
public class DishSpecOptionController {

    @Resource
    DishSpecOptionService dishSpecOptionService;

    @Resource
    DishService dishService;

    @Resource
    ResourceBindingCoordinator resourceBindings;

    @Resource
    StoreDataScopeService storeDataScopeService;

    /**
     * 添加规格选项
     * @param request 规格选项请求
     * @return 结果
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:dish:spec"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addSpecOption(@RequestBody DishSpecOptionRequest request) {
        // 验证规格值列表
        validateSpecValues(request.getSpecValueList());

        // 转换为实体对象
        DishSpecOption specOption = request.toDishSpecOption();
        ensureDishAccessible(specOption.getDishId());
        specOption.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        ResourceBindingPlan imagePlan = prepareImages(specOption.getId(), request.getSpecValueList(), null);
        specOption.setCreatedAt(LocalDateTime.now());
        specOption.setUpdatedAt(LocalDateTime.now());
        //将规格值列表转换为字符串
        specOption.setSpecValues(SpecValueConverter.convertToString(request.getSpecValueList()));
        boolean result = dishSpecOptionService.save(specOption);
        if (result) resourceBindings.completeAfterCommit(imagePlan);
        if (result) {
            return Result.success("添加规格选项成功", result);
        } else {
            return Result.error("添加规格选项失败");
        }
    }

    /**
     * 更新规格选项
     * @param dishSpecOption 规格选项请求
     * @return 结果
     */
    @PutMapping("/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:dish:spec"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateSpecOption(@RequestBody DishSpecOption dishSpecOption) {
        // 验证ID
        if (dishSpecOption.getId() == null) {
            return Result.error("规格选项ID不能为空");
        }
        DishSpecOption existing = dishSpecOptionService.getById(dishSpecOption.getId());
        if (existing == null) {
            return Result.error("规格选项不存在");
        }
        ensureDishAccessible(existing.getDishId());
        if (dishSpecOption.getDishId() != null) {
            ensureDishAccessible(dishSpecOption.getDishId());
        }

        dishSpecOption.setUpdatedAt(LocalDateTime.now());
        ResourceBindingPlan imagePlan = prepareImages(dishSpecOption.getId(), dishSpecOption.getSpecValueList(),
                existing.getSpecValueList());
        dishSpecOption.setSpecValues(SpecValueConverter.convertToString(dishSpecOption.getSpecValueList()));

        boolean result = dishSpecOptionService.updateById(dishSpecOption);
        if (result) resourceBindings.completeAfterCommit(imagePlan);
        if (result) {
            return Result.success("更新规格选项成功", result);
        } else {
            return Result.error("更新规格选项失败");
        }
    }

    /**
     * 删除规格选项
     * @param id 规格选项ID
     * @return 结果
     */
    @DeleteMapping("/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:dish:spec"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteSpecOption(@PathVariable Long id) {
        DishSpecOption existing = dishSpecOptionService.getById(id);
        if (existing == null) {
            return Result.error("规格选项不存在");
        }
        ensureDishAccessible(existing.getDishId());
        boolean result = dishSpecOptionService.removeById(id);
        if (result) resourceBindings.clearAfterCommit("SPEC", id, "specValues");
        if (result) {
            return Result.success("删除规格选项成功");
        } else {
            return Result.error("删除规格选项失败");
        }
    }

    /** 规格图片按值列表位置绑定，JSON 中仍只保留旧客户端认识的 URL。 */
    private ResourceBindingPlan prepareImages(Long optionId, List<SpecValue> values, List<SpecValue> existing) {
        List<ResourceBinding> slots = new java.util.ArrayList<>();
        List<ResourceBinding> previous = existing == null ? List.of()
                : resourceBindings.fieldReferences("SPEC", optionId, "specValues");
        if (values != null) {
            for (int index = 0; index < values.size(); index++) {
                String url = values.get(index).getImageUrl();
                if (url != null && !url.isBlank()) {
                    int position = index;
                    Long resourceId = existing != null && index < existing.size()
                            && java.util.Objects.equals(url, existing.get(index).getImageUrl())
                            ? previous.stream().filter(item -> item.sortOrder() == position).findFirst()
                                .map(ResourceBinding::resourceId).orElse(null) : null;
                    slots.add(new ResourceBinding("specValues", index, resourceId, url));
                }
            }
        }
        ResourceBindingPlan plan = resourceBindings.prepare(resourceBindings.request("SPEC", optionId, "specValues", slots));
        if (values != null) for (ResourceBinding slot : plan.bindings())
            values.get(slot.sortOrder()).setImageUrl(slot.url());
        return plan;
    }

/*    *//**
     * 根据菜品ID查询规格选项列表
     * @param dishId 菜品ID
     * @return 规格选项列表
     *//*
    @GetMapping("/listByDish")
    public Result<List<DishSpecOption>> getSpecOptionsByDishId(@RequestParam Long dishId) {
        if(dishId == null){
            return Result.error("菜品id不能为空");
        }
        List<DishSpecOption> dishSpecOptions = dishSpecOptionService.selectSpecOptionsByDishId(dishId);

        return Result.success("查询规格选项列表成功", dishSpecOptions);
    }*/

    /**
     * 验证规格值列表
     * @param specValueList 规格值列表
     */
    private void validateSpecValues(List<SpecValue> specValueList) {
        if (specValueList == null || specValueList.isEmpty()) {
            throw new IllegalArgumentException("规格值列表不能为空");
        }

        // 验证每个规格值
        for (SpecValue specValue : specValueList) {
            if (specValue.getValue() == null || specValue.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("规格值不能为空");
            }

            // 如果额外价格为空，设置为0
            if (specValue.getExtraPrice() == null) {
                specValue.setExtraPrice(BigDecimal.ZERO);
            }

            // 如果是否默认选中为空，设置为false
            if (specValue.getIsDefault() == null) {
                specValue.setIsDefault(false);
            }

            // 如果排序值为空，设置为0
            if (specValue.getSortOrder() == null) {
                specValue.setSortOrder(0);
            }
        }

        // 检查是否有多个默认选中的值
        long defaultCount = specValueList.stream()
                .filter(SpecValue::getIsDefault)
                .count();

        if (defaultCount > 1) {
            throw new IllegalArgumentException("最多只能有一个默认选中的规格值");
        }
    }

    private void ensureDishAccessible(Long dishId) {
        Dish dish = dishId == null ? null : dishService.getById(dishId);
        if (dish == null || Integer.valueOf(1).equals(dish.getDeleted())) {
            throw new SecurityException("菜品不存在或无权访问");
        }
        if (storeDataScopeService.requiresStoreRestriction()) {
            List<Long> stores = storeDataScopeService.getAllowedStoreIds();
            if (stores.size() != 1) throw new SecurityException("请选择当前有权限的门店");
            dishService.getEffectiveDish(dishId, stores.getFirst(), false);
            int sourceType = dish.getSourceType() == null ? (dish.getStoreId() == null ? 0 : 1) : dish.getSourceType();
            if (sourceType == 0) throw new SecurityException("门店不能修改总部菜品规格");
        }
    }
}
