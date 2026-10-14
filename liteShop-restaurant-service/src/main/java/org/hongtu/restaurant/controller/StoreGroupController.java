package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.StoreGroup;
import org.hongtu.restaurant.VO.StoreGroupSaveRequest;
import org.hongtu.restaurant.service.StoreGroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 门店分组管理接口。
 */
@RestController
@RequestMapping("/dish/store-group")
public class StoreGroupController {

    @Resource
    private StoreGroupService storeGroupService;

    /** 查询用于批量菜单发布的门店分组。 */
    @GetMapping("/list")
    @SaCheckPermission(value = {"restaurant:store", "restaurant:store:edit",
            "restaurant:dish", "restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<StoreGroup>> list() {
        return Result.success(storeGroupService.listGroups());
    }

    /** 新增或更新门店分组。 */
    @PutMapping
    @SaCheckPermission(value = {"restaurant:store:edit", "restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<StoreGroup> save(@RequestBody StoreGroupSaveRequest request) {
        return Result.success("门店分组保存成功", storeGroupService.saveGroup(request));
    }

    /** 删除门店分组。 */
    @DeleteMapping("/{id}")
    @SaCheckPermission(value = {"restaurant:store:edit", "restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success("门店分组删除成功", storeGroupService.deleteGroup(id));
    }
}
