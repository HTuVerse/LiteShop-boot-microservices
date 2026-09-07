package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;

import org.hongtu.DAO.UserActivityChanceMapper;
import org.hongtu.DTO.UserActivityChance;
import org.hongtu.Result.Result;
import org.hongtu.service.UserActivityChanceService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户抽奖机会活动关联表 前端控制器
 *
 * @author Honghong
 * @since 2025-07-02
 */
@RestController
@RequestMapping("/user-activity-chance")
public class UserActivityChanceController {

    @Resource
    private UserActivityChanceService userActivityChanceService;
    @Resource
    UserActivityChanceMapper userActivityChanceMapper;

    /**
     * 获取用户在指定活动中的抽奖次数
     */
    @GetMapping("/getUserChances")
    public Result<Map<String, Object>> getUserChances(@RequestParam Long activityId) {
        Long userId = StpUtil.getLoginIdAsLong();
        Integer chances = userActivityChanceMapper.getUserChances(userId, activityId);
        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        result.put("chances", chances);

        return Result.success(result);
    }

    /**
     * 增加用户抽奖次数（管理员操作）
     */
    @PostMapping("/increaseUserChances")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:chance"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> increaseUserChances(@RequestParam Long userId,
                                               @RequestParam Long activityId,
                                               @RequestParam Integer chances) {
        boolean success = userActivityChanceService.increaseUserChances(userId, activityId, chances);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("增加抽奖次数失败");
        }
    }

    /**
     * 减少用户抽奖次数（管理员操作）
     */
    @PostMapping("/decreaseUserChances")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:chance"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> decreaseUserChances(@RequestParam Long userId,
                                               @RequestParam Long activityId,
                                               @RequestParam Integer chances) {
        boolean success = userActivityChanceService.decreaseUserChances(userId, activityId, chances);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("减少抽奖次数失败，可能次数不足");
        }
    }

    /**
     * 获取活动的所有用户抽奖机会（分页，管理员操作）
     */
    @GetMapping("/getActivityUserChances")
    @SaCheckPermission(value="marketing:lottery", orRole="SUPER_ADMIN")
    public Result<Page<UserActivityChance>> getActivityUserChances(
            @RequestParam Long activityId,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return userActivityChanceService.getActivityUserChances(activityId, currentPage, pageSize);
    }

    /**
     * 批量增加用户抽奖机会（管理员操作）
     *
     * @param activityId 活动ID
     * @param chances    增加的次数
     * @param allUsers   是否全体用户（true=全体用户，false=仅指定用户列表）
     * @param userIds    用户ID列表（allUsers为false时必填）
     */
    @PostMapping("/batchIncreaseChances")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:chance"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> batchIncreaseChances(
            @RequestParam Long activityId,
            @RequestParam Integer chances,
            @RequestParam Boolean allUsers,
            @RequestParam(required = false) List<Long> userIds) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }
        if (chances == null || chances <= 0) {
            return Result.error("增加次数必须大于0");
        }
        if (!allUsers && (userIds == null || userIds.isEmpty())) {
            return Result.error("非全体用户模式下，用户ID列表不能为空");
        }

        boolean success = userActivityChanceService.batchIncreaseChances(activityId, chances, allUsers, userIds);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("批量增加抽奖次数失败");
        }
    }

}
