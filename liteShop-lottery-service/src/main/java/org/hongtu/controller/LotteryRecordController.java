package org.hongtu.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.LotteryRecord;
import org.hongtu.Result.Result;
import org.hongtu.exception.BusinessException;
import org.hongtu.service.LotteryRecordService;
import org.hongtu.utils.RateLimitUtil;
import org.springframework.web.bind.annotation.*;

/**
 * 抽奖记录表 前端控制器
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Slf4j
@RestController
@RequestMapping("/lottery-record")
public class LotteryRecordController {

    @Resource
    LotteryRecordService lotteryRecordService;

    @Resource
    RateLimitUtil rateLimitUtil;

    /**
     * 抽奖接口限流配置：每个用户每秒最多2次请求
     */
    private static final int LOTTERY_RATE_LIMIT = 2;
    private static final int LOTTERY_RATE_WINDOW = 1;

    /**
     * 幂等性保护过期时间（秒）
     */
    private static final int IDEMPOTENT_EXPIRE = 3;

    /**
     * 执行抽奖
     * 添加了限流和幂等性保护
     */
    @PostMapping("/drawLottery")
    public Result<LotteryRecord> drawLottery(@RequestParam Long activityId) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }

        Long userId = StpUtil.getLoginIdAsLong();

        // 1. 接口限流检查（每个用户每秒最多2次请求）
        if (!rateLimitUtil.checkUserApiLimit(userId, "drawLottery", LOTTERY_RATE_LIMIT, LOTTERY_RATE_WINDOW)) {
            log.warn("抽奖接口限流触发，userId={}, activityId={}", userId, activityId);
            return Result.error("操作过于频繁，请稍后再试");
        }

        // 2. 幂等性保护（防止重复点击）
        String idempotentKey = "lottery:" + userId + ":" + activityId;
        if (!rateLimitUtil.setIdempotentMark(idempotentKey, IDEMPOTENT_EXPIRE)) {
            log.warn("抽奖重复请求被拦截，userId={}, activityId={}", userId, activityId);
            return Result.error("请求处理中，请勿重复点击");
        }

        try {
            return lotteryRecordService.drawLottery(userId, activityId);
        } catch (BusinessException e) {
            log.warn("抽奖业务异常，userId={}, activityId={}, reason={}", userId, activityId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("抽奖异常，userId={}, activityId={}", userId, activityId, e);
            return Result.error("抽奖失败，请稍后再试");
        } finally {
            // 请求结束后删除幂等性标记（或者让它自然过期也可以）
            // 这里选择让它自然过期，可以更好地防止快速重复点击
        }
    }

    /**
     * 获取用户在指定活动中的抽奖记录（分页）
     */
    @GetMapping("/getUserLotteryRecordsByActivity")
    public Result<Page<LotteryRecord>> getUserLotteryRecordsByActivity(
            @RequestParam Long activityId,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }

        try {
            Long userId = StpUtil.getLoginIdAsLong();
            return lotteryRecordService.getUserLotteryRecordsByActivity(userId, activityId, currentPage, pageSize);
        } catch (Exception e) {
            log.error("获取活动抽奖记录异常，activityId={}", activityId, e);
            return Result.error("获取记录失败");
        }
    }

    /**
     * 管理员获取指定活动的所有抽奖记录（分页，支持多条件筛选）
     * @param activityId 活动ID
     * @param userId 用户ID（可选，指定时只查询该用户的记录）
     * @param prizeType 奖品类型（可选，0-优惠券，1-积分，2-实物，3-谢谢参与）
     * @param isReceived 领取状态（可选，0-未领取，1-已领取）
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     */
    @GetMapping("/getActivityAllRecords")
    @SaCheckPermission(value = {"marketing:lottery"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<LotteryRecord>> getActivityAllRecords(
            @RequestParam Long activityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer prizeType,
            @RequestParam(required = false) Integer isReceived,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }

        try {
            return lotteryRecordService.getActivityAllRecords(activityId, userId, prizeType, isReceived, currentPage, pageSize);
        } catch (Exception e) {
            log.error("管理员获取活动抽奖记录异常，activityId={}, userId={}, prizeType={}, isReceived={}",
                    activityId, userId, prizeType, isReceived, e);
            return Result.error("获取记录失败");
        }
    }

    /**
     * 领取奖品
     */
    @PostMapping("/receivePrize")
    public Result<Boolean> receivePrize(@RequestParam Long recordId) {
        // 参数校验
        if (recordId == null || recordId <= 0) {
            return Result.error("记录ID无效");
        }

        try {
            Long userId = StpUtil.getLoginIdAsLong();
            boolean success = lotteryRecordService.receivePrize(userId, recordId);
            if (success) {
                return Result.success(true);
            } else {
                return Result.error("领取失败，可能已经领取过或记录不存在");
            }
        } catch (Exception e) {
            log.error("领取奖品异常，recordId={}", recordId, e);
            return Result.error("领取失败，请稍后再试");
        }
    }


    /**
     * 管理员清空指定活动的所有抽奖记录
     */
    @DeleteMapping("/clearActivityRecords")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:lottery:clear-records"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> clearActivityRecords(@RequestParam Long activityId) {
        // 参数校验
        if (activityId == null || activityId <= 0) {
            return Result.error("活动ID无效");
        }

        try {
            boolean success = lotteryRecordService.clearActivityRecords(activityId);
            if (success) {
                return Result.success(true);
            } else {
                return Result.error("清空失败");
            }
        } catch (Exception e) {
            log.error("清空活动抽奖记录异常，activityId={}", activityId, e);
            return Result.error("清空失败，请稍后再试");
        }
    }
}
