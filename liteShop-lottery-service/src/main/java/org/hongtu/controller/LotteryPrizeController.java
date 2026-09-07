package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.hongtu.DTO.LotteryPrize;
import org.hongtu.Result.Result;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.service.LotteryPrizeService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 抽奖奖品表 前端控制器
 *
 * @author Honghong
 * @since 2025-07-02
 */
@RestController
@RequestMapping("/lottery-prize")
public class LotteryPrizeController {

    @Resource
    private LotteryPrizeService lotteryPrizeService;

    /**
     * 获取所有奖品列表
     */
    @GetMapping("/getAllPrizes")
    @SaCheckPermission(value = {"marketing:lottery"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<LotteryPrize>> getAllPrizes() {
        List<LotteryPrize> prizes = lotteryPrizeService.list();
        return Result.success(prizes);
    }

    /**
     * 获取所有启用的奖品列表
     */
    @GetMapping("/getEnabledPrizes")
    @SaCheckPermission(value = {"marketing:lottery"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<LotteryPrize>> getEnabledPrizes() {
        LambdaQueryWrapper<LotteryPrize> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LotteryPrize::getEnabled, BooleanEnum.YES);

        List<LotteryPrize> prizes = lotteryPrizeService.list(queryWrapper);
        return Result.success(prizes);
    }

    /**
     * 获取奖品详情
     */
    @GetMapping("/getPrizeDetail")
    @SaCheckPermission(value = {"marketing:lottery"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<LotteryPrize> getPrizeDetail(@RequestParam Long id) {
        LotteryPrize prize = lotteryPrizeService.getById(id);
        if (prize == null) {
            return Result.error("奖品不存在");
        }
        return Result.success(prize);
    }

    /**
     * 添加奖品
     */
    @PostMapping("/addPrize")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:prize"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addPrize(@RequestBody LotteryPrize prize) {
        prize.setCreateTime(LocalDateTime.now());
        prize.setUpdateTime(LocalDateTime.now());

        boolean success = lotteryPrizeService.save(prize);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("添加奖品失败");
        }
    }

    /**
     * 更新奖品
     */
    @PutMapping("/updatePrize")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:prize"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updatePrize(@RequestBody LotteryPrize prize) {
        prize.setUpdateTime(LocalDateTime.now());

        boolean success = lotteryPrizeService.updateById(prize);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("更新奖品失败");
        }
    }

    /**
     * 启用/禁用奖品（自动切换状态）
     */
    @PutMapping("/togglePrizeStatus")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:prize"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> togglePrizeStatus(@RequestParam Long id) {
        // 先查询当前奖品
        LotteryPrize existingPrize = lotteryPrizeService.getById(id);
        if (existingPrize == null) {
            return Result.error("奖品不存在");
        }

        // 切换状态：YES -> NO, NO -> YES
        LotteryPrize prize = new LotteryPrize();
        prize.setId(id);
        prize.setEnabled(existingPrize.getEnabled() == BooleanEnum.YES ? BooleanEnum.NO : BooleanEnum.YES);
        prize.setUpdateTime(LocalDateTime.now());

        boolean success = lotteryPrizeService.updateById(prize);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("更新奖品状态失败");
        }
    }

    /**
     * 删除奖品
     */
    @DeleteMapping("/delete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:prize"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deletePrize(@RequestParam Long id) {
        boolean success = lotteryPrizeService.removeById(id);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("删除奖品失败");
        }
    }
}
