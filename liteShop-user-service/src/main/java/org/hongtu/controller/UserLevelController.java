package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.DTO.UserLevel;
import org.hongtu.Result.Result;
import org.hongtu.service.UserLevelService;
import org.springframework.web.bind.annotation.*;

/**
 * 用户等级配置表 前端控制器
 *
 * @author Ht
 * @since 2025-08-23
 */
@RestController
@RequestMapping("/user-level")
public class UserLevelController {
    @Resource
    UserLevelService userLevelService;

    /**
     * 获取所有等级配置
     */
    @GetMapping("/list")
    public Result listLevels() {
        return userLevelService.listLevels();
    }

    /**
     * 获取所有启用的等级配置
     */
    @GetMapping("/listEnabledLevels")
    public Result listEnabledLevels() {
        return userLevelService.listEnabledLevels();
    }


    /*管理员接口*/

    /**
     * 添加等级配置
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result addLevel(@RequestBody UserLevel userLevel) {
        return userLevelService.addLevel(userLevel);
    }

    /**
     * 更新等级配置
     */
    @PutMapping("/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result updateLevel(@RequestBody UserLevel userLevel) {
        return userLevelService.updateLevel(userLevel);
    }

    /**
     * 删除等级配置
     */
    @DeleteMapping("/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteLevel(@PathVariable Long id) {
        return userLevelService.deleteLevel(id);
    }

    /**
     * 启用/禁用等级配置
     */
    @PutMapping("/editStatus/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result updateLevelStatus(@PathVariable Long id) {
        return userLevelService.updateLevelStatus(id);
    }

    /**
     * 更新用户等级
     */
    @PutMapping("/updateUserLevel")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:level"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result updateUserLevel(@RequestParam Long userId,@RequestParam Integer levelCode) {
        return userLevelService.updateUserLevel(userId,levelCode);
    }

}
