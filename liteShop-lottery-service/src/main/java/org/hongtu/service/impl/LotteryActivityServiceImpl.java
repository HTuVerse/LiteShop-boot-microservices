package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.hongtu.DAO.LotteryActivityMapper;
import org.hongtu.DTO.LotteryActivity;
import org.hongtu.Result.Result;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.service.LotteryActivityService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 抽奖活动表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Service
public class LotteryActivityServiceImpl extends ServiceImpl<LotteryActivityMapper, LotteryActivity> implements LotteryActivityService {

    @Resource
    LotteryActivityMapper lotteryActivityMapper;

    @Override
    public Result<List<LotteryActivity>> getAllActivities() {
        LambdaQueryWrapper<LotteryActivity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(LotteryActivity::getCreateTime);
        return Result.success(list(queryWrapper));
    }

    @Override
    public Result<List<LotteryActivity>> getEnabledActivities(){
        LambdaQueryWrapper<LotteryActivity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LotteryActivity::getStatus, BooleanEnum.YES)
                .orderByDesc(LotteryActivity::getCreateTime);
        return Result.success(list(queryWrapper));
    }

    @Override
    public Result<Boolean> addActivity(LotteryActivity activity) {
        // 设置默认值
        if (activity.getStatus() == null) {
            activity.setStatus(BooleanEnum.NO);
        }
        activity.setCreateTime(LocalDateTime.now());
        activity.setUpdateTime(LocalDateTime.now());

        boolean success = save(activity);
        if (success) {
            return Result.success(true);
        }
        return Result.error("新增活动失败");
    }

    @Override
    public Result<Boolean> editActivity(LotteryActivity activity) {
        if (activity.getId() == null) {
            return Result.error("活动ID不能为空");
        }

        LotteryActivity existActivity = getById(activity.getId());
        if (existActivity == null) {
            return Result.error("活动不存在");
        }

        org.hongtu.security.OperationPermission.requireChange("marketing:lottery:status", existActivity.getStatus(), activity.getStatus());
        activity.setUpdateTime(LocalDateTime.now());
        boolean success = updateById(activity);
        if (success) {
            return Result.success(true);
        }
        return Result.error("编辑活动失败");
    }

    @Override
    public Result<Boolean> toggleActivityStatus(Long activityId) {
        LotteryActivity lotteryActivity = lotteryActivityMapper.selectById(activityId);
        if (lotteryActivity == null) {
            return Result.error("活动不存在");
        }

        lotteryActivity.setStatus(lotteryActivity.getStatus() == BooleanEnum.NO ? BooleanEnum.YES : BooleanEnum.NO);
        lotteryActivity.setUpdateTime(LocalDateTime.now());
        boolean success = updateById(lotteryActivity);
        if (success) {
            return Result.success(true);
        }
        return Result.error("切换状态失败");
    }

    @Override
    public Result<Boolean> deleteActivity(Long activityId) {
        LotteryActivity activity = getById(activityId);
        if (activity == null) {
            return Result.error("活动不存在");
        }

        boolean success = removeById(activityId);
        if (success) {
            return Result.success(true);
        }
        return Result.error("删除活动失败");
    }

    @Override
    public boolean isActivityEnabled(Long activityId) {
        LambdaQueryWrapper<LotteryActivity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LotteryActivity::getId, activityId)
                .eq(LotteryActivity::getStatus, BooleanEnum.YES);

        return count(queryWrapper) > 0;
    }

}
