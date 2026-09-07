package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.UserActivityChanceMapper;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.DTO.UserActivityChance;
import org.hongtu.Result.Result;
import org.hongtu.service.UserActivityChanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 用户抽奖机会活动关联表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Slf4j
@Service
public class UserActivityChanceServiceImpl extends ServiceImpl<UserActivityChanceMapper, UserActivityChance> implements UserActivityChanceService {

    @Resource
    UserActivityChanceMapper userActivityChanceMapper;

    @Resource
    UserFeignClient userFeignClient;

    /**
     * 批量操作的分批大小
     */
    private static final int BATCH_SIZE = 500;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean increaseUserChances(Long userId, Long activityId, Integer chances) {
        if (chances == null || chances <= 0) {
            return false;
        }
        // 【优化】使用 INSERT ... ON DUPLICATE KEY UPDATE 解决并发问题
        // 需要确保表上有唯一索引：UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`)
        int rows = userActivityChanceMapper.insertOrUpdateChances(userId, activityId, chances);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean decreaseUserChances(Long userId, Long activityId, Integer chances) {
        if (chances == null || chances <= 0) {
            return false;
        }
        // 【优化】直接使用乐观锁扣减，无需先查询
        // SQL 条件 chances >= #{chances} 已经保证了次数充足才能扣减
        int rows = userActivityChanceMapper.decreaseUserChances(userId, activityId, chances);
        return rows > 0;
    }

    @Override
    public Result<Page<UserActivityChance>> getActivityUserChances(Long activityId, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<UserActivityChance> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserActivityChance::getActivityId, activityId)
                .orderByDesc(UserActivityChance::getChances);

        Page<UserActivityChance> page = new Page<>(currentPage, pageSize);
        return Result.success(page(page, queryWrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchIncreaseChances(Long activityId, Integer chances, Boolean allUsers, List<Long> userIds) {
        if (chances == null || chances <= 0) {
            return false;
        }
        
        try {
            List<Long> targetUserIds;
            if (Boolean.TRUE.equals(allUsers)) {
                // 获取所有用户ID（微服务改造：经 Feign 调 user-service）
                targetUserIds = FeignResultUtil.unwrap(userFeignClient.getAllUserIds());
            } else {
                targetUserIds = userIds;
            }

            if (targetUserIds == null || targetUserIds.isEmpty()) {
                return false;
            }

            // 【优化】分批处理，避免单次事务过大
            int totalCount = targetUserIds.size();
            int successCount = 0;
            
            for (int i = 0; i < totalCount; i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, totalCount);
                List<Long> batchUserIds = targetUserIds.subList(i, endIndex);
                
                // 使用 INSERT ... ON DUPLICATE KEY UPDATE 批量处理
                for (Long userId : batchUserIds) {
                    int rows = userActivityChanceMapper.insertOrUpdateChances(userId, activityId, chances);
                    if (rows > 0) {
                        successCount++;
                    }
                }
                
                log.debug("批量增加抽奖机会进度：{}/{}", endIndex, totalCount);
            }

            log.info("批量增加抽奖机会完成，activityId={}, chances={}, totalCount={}, successCount={}",
                    activityId, chances, totalCount, successCount);
            return successCount > 0;
        } catch (Exception e) {
            log.error("批量增加抽奖机会失败，activityId={}, chances={}", activityId, chances, e);
            throw e; // 抛出异常以触发事务回滚
        }
    }

}
