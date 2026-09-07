package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.hongtu.DAO.ActivityPrizeMapper;
import org.hongtu.DTO.ActivityPrize;
import org.hongtu.Result.Result;
import org.hongtu.VO.ActivityLotteryPrizeVO;
import org.hongtu.VO.ActivityPrizeUserVO;
import org.hongtu.service.ActivityPrizeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 活动奖品关联表 前端控制器
 *
 * @author Honghong
 * @since 2025-07-02
 */
@RestController
@RequestMapping("/activity-prize")
public class ActivityPrizeController {

    @Resource
    private ActivityPrizeService activityPrizeService;
    @Resource
    ActivityPrizeMapper activityPrizeMapper;

    /**
     * 【用户端】获取活动关联的所有奖品（隐藏敏感信息如中奖概率）
     */
    @GetMapping("/getActivityPrizes")
    @SaIgnore
    public Result<List<ActivityPrizeUserVO>> getActivityPrizesForUser(@RequestParam Long activityId) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }
        // 返回不含敏感信息的奖品列表
        return activityPrizeService.getActivityPrizesForUser(activityId);
    }

    /**
     * 【管理员】获取活动关联的所有奖品及完整信息（包含概率、库存等）
     */
    @GetMapping("/admin/getActivityPrizes")
    @SaCheckPermission(value="marketing:lottery", orRole="SUPER_ADMIN")
    public Result<List<ActivityLotteryPrizeVO>> getActivityPrizes(@RequestParam Long activityId) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }
        return activityPrizeService.getActivityPrizes(activityId);
    }

    /**
     * 为活动添加奖品（管理员操作）
     */
    @PostMapping("/addPrizeToActivity")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:prize"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addPrizeToActivity(@RequestBody ActivityPrize activityPrize) {
        // 参数校验
        if (activityPrize == null) {
            return Result.error("参数不能为空");
        }
        if (activityPrize.getActivityId() == null || activityPrize.getActivityId() <= 0) {
            return Result.error("活动ID无效");
        }
        if (activityPrize.getPrizeId() == null || activityPrize.getPrizeId() <= 0) {
            return Result.error("奖品ID无效");
        }
        return activityPrizeService.addPrizeToActivity(activityPrize);
    }

    /**
     * 从活动中移除奖品（管理员操作）
     */
    @DeleteMapping("/removePrizeFromActivity")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:prize"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> removePrizeFromActivity(@RequestParam Long activityId, @RequestParam Long prizeId) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }
        if (prizeId == null || prizeId <= 0) {
            return Result.error("奖品ID无效");
        }

        LambdaQueryWrapper<ActivityPrize> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivityPrize::getActivityId, activityId)
                .eq(ActivityPrize::getPrizeId, prizeId);

        boolean success = activityPrizeService.remove(queryWrapper);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("从活动中移除奖品失败");
        }
    }

    /**
     * 从活动中移除所有奖品（管理员操作）
     */
    @DeleteMapping("/removeAllPrizesFromActivity")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:prize"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> removeAllPrizesFromActivity(@RequestParam Long activityId) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }

        LambdaQueryWrapper<ActivityPrize> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivityPrize::getActivityId, activityId);

        boolean success = activityPrizeService.remove(queryWrapper);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("从活动中移除所有奖品失败");
        }
    }

    /**
     * 增加库存（管理员操作）
     */
    @PostMapping("/increaseInventory")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:stock"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> increaseInventory(@RequestParam Long activityId,
                                             @RequestParam Long prizeId,
                                             @RequestParam Long amount) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }
        if (prizeId == null || prizeId <= 0) {
            return Result.error("奖品ID无效");
        }
        if (amount == null || amount <= 0) {
            return Result.error("增加数量必须大于0");
        }

        int i = activityPrizeMapper.increaseInventory(activityId, prizeId, amount);
        if (i > 0) {
            return Result.success(true);
        } else {
            return Result.error("增加库存失败");
        }
    }

    /**
     * 减少库存（管理员操作）
     * 注意：此操作会同时减少总库存和剩余库存
     */
    @PostMapping("/deductInventory")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:stock"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> decreaseInventory(@RequestParam Long activityId,
                                             @RequestParam Long prizeId,
                                             @RequestParam Long amount) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }
        if (prizeId == null || prizeId <= 0) {
            return Result.error("奖品ID无效");
        }
        if (amount == null || amount <= 0) {
            return Result.error("扣减数量必须大于0");
        }

        // 使用管理员专用的扣减方法，同时扣减总库存和剩余库存
        int i = activityPrizeMapper.deductInventoryByAdmin(activityId, prizeId, amount);
        if (i > 0) {
            return Result.success(true);
        } else {
            return Result.error("扣减库存失败，可能库存不足");
        }
    }
}
