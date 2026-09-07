package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.UserPointsRecord;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.UserPointsRecordMapper;
import org.hongtu.Result.Result;
import org.hongtu.enums.PointsOperationEnum;
import org.hongtu.enums.PointsSourceEnum;
import org.hongtu.service.UserPointsRecordService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 用户积分记录DTO 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-08-06
 */
@Service
public class UserPointsRecordServiceImpl extends ServiceImpl<UserPointsRecordMapper, UserPointsRecord> implements UserPointsRecordService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public Result getUserPointsRecords(Long userId, Integer pageNum, Integer pageSize,PointsSourceEnum source) {
        // 参数校验（确保必要参数不为空）
        if (userId == null || pageNum == null || pageSize == null) {
            return Result.error("必要参数不能为空");
        }
        try {
            // 创建分页对象
            Page<UserPointsRecord> page = new Page<>(pageNum, pageSize);
            // 动态构建查询条件
            LambdaQueryWrapper<UserPointsRecord> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UserPointsRecord::getUserId, userId)
                    .orderByDesc(UserPointsRecord::getOperationTime);
            // 当source不为null时添加条件
            if (source != null) {
                queryWrapper.eq(UserPointsRecord::getSource, source);
            }
            // 执行分页查询
            Page<UserPointsRecord> recordPage = page(page, queryWrapper);
            return Result.success("查询成功", recordPage);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Override
    public Result getUserPointsStatistics(Long userId) {
        // 参数校验
        if (userId == null) {
            return Result.error("参数错误");
        }

        try {
            // 查询用户
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            // 统计总收入积分
            LambdaQueryWrapper<UserPointsRecord> incomeWrapper = new LambdaQueryWrapper<>();
            incomeWrapper.eq(UserPointsRecord::getUserId, userId)
                    .eq(UserPointsRecord::getOperationType, PointsOperationEnum.INCREASE);
            int totalIncome = lambdaQuery()
                    .eq(UserPointsRecord::getUserId, userId)
                    .eq(UserPointsRecord::getOperationType, PointsOperationEnum.INCREASE)
                    .list()
                    .stream()
                    .mapToInt(UserPointsRecord::getPoints)
                    .sum();

            // 统计总支出积分
            int totalExpense = lambdaQuery()
                    .eq(UserPointsRecord::getUserId, userId)
                    .eq(UserPointsRecord::getOperationType, PointsOperationEnum.DECREASE)
                    .list()
                    .stream()
                    .mapToInt(UserPointsRecord::getPoints)
                    .sum();

            // 按来源统计积分
            Map<PointsSourceEnum, Integer> sourceStatistics = new HashMap<>();
            for (PointsSourceEnum source : PointsSourceEnum.values()) {
                int sourceTotal = lambdaQuery()
                        .eq(UserPointsRecord::getUserId, userId)
                        .eq(UserPointsRecord::getSource, source)
                        .eq(UserPointsRecord::getOperationType, PointsOperationEnum.INCREASE)
                        .list()
                        .stream()
                        .mapToInt(UserPointsRecord::getPoints)
                        .sum();
                sourceStatistics.put(source, sourceTotal);
            }

            // 构建统计结果
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("currentPoints", user.getPoints());
            statistics.put("totalIncome", totalIncome);
            statistics.put("totalExpense", totalExpense);
            statistics.put("sourceStatistics", sourceStatistics);

            return Result.success("查询成功", statistics);
        } catch (Exception e) {
            log.error("获取用户积分统计信息异常", e);
            return Result.error("系统异常，查询失败");
        }
    }
}
