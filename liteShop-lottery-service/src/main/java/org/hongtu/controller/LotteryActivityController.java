package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import org.hongtu.DTO.LotteryActivity;
import org.hongtu.Result.Result;
import org.hongtu.service.LotteryActivityService;
import org.hongtu.service.UserActivityChanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *
 * 抽奖活动表 前端控制器
 * @author Honghong
 * @since 2025-07-02
 */
@RestController
@RequestMapping("/lottery-activity")
public class LotteryActivityController {

    @Resource
    private LotteryActivityService lotteryActivityService;

    @Resource
    private UserActivityChanceService userActivityChanceService;

    /**
     * 获取所有抽奖活动（管理端使用，包括禁用的活动）
     */
    @GetMapping("/list")
    @SaCheckPermission(value = {"marketing:lottery"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<LotteryActivity>> getAllActivities() {
        return lotteryActivityService.getAllActivities();
    }

    /**
     * 获取所有启用的抽奖活动（C端用户使用）
     */
    @GetMapping("/list/enabled")
    @SaIgnore
    public Result<List<LotteryActivity>> getEnabledActivities() {
        return lotteryActivityService.getEnabledActivities();
    }

    /**
     * 获取活动详情
     */
    @GetMapping("/getActivityDetail")
    @SaIgnore
    public Result<LotteryActivity> getActivityDetail(@RequestParam Long activityId) {
        LotteryActivity activity = lotteryActivityService.getById(activityId);
        if (activity == null) {
            return Result.error("活动不存在");
        }
        return Result.success(activity);
    }

    /**
     * 新增抽奖活动
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addActivity(@RequestBody LotteryActivity activity) {
        return lotteryActivityService.addActivity(activity);
    }

    /**
     * 编辑活动（完整编辑）
     */
    @PutMapping("/editActivity")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> editActivity(@RequestBody LotteryActivity activity) {
        return lotteryActivityService.editActivity(activity);
    }

    /**
     * 切换活动状态（启用/禁用）
     */
    @PutMapping("/toggleActivityStatus")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> toggleActivityStatus(@RequestParam Long activityId) {
        return lotteryActivityService.toggleActivityStatus(activityId);
    }

    /**
     * 删除活动
     */
    @DeleteMapping("/deleteActivity")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteActivity(@RequestParam Long activityId) {
        return lotteryActivityService.deleteActivity(activityId);
    }

}
