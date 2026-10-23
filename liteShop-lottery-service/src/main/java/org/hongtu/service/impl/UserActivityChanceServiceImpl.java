package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.UserActivityChanceMapper;
import org.hongtu.DAO.LotteryChanceGrantMapper;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.DTO.UserActivityChance;
import org.hongtu.Result.Result;
import org.hongtu.service.UserActivityChanceService;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;

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

    @Resource
    LotteryChanceGrantMapper chanceGrantMapper;

    private final TransactionTemplate batchTransaction;

    public UserActivityChanceServiceImpl(PlatformTransactionManager manager) {
        batchTransaction = new TransactionTemplate(manager);
        batchTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

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
        int rows = userActivityChanceMapper.insertOrUpdateChances(IdWorker.getId(), userId, activityId, chances);
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
    public boolean batchIncreaseChances(Long activityId, Integer chances, Boolean allUsers,
                                        List<Long> userIds, String requestKey) {
        if (activityId == null || chances == null || chances <= 0 || requestKey == null
                || !requestKey.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BusinessException("批量发放参数或请求键无效");
        }
        long processed = 0;
        boolean hadTargets = false;
        if (Boolean.TRUE.equals(allUsers)) {
            long afterId = 0;
            while (true) {
                List<Long> page = FeignResultUtil.unwrap(userFeignClient.getUserIdPage(afterId, BATCH_SIZE));
                if (page == null || page.isEmpty()) break;
                hadTargets = true;
                processed += grantBatch(page, activityId, chances, requestKey);
                long next = page.getLast();
                if (next <= afterId) throw new IllegalStateException("用户游标未前进");
                afterId = next;
                log.info("批量发放抽奖机会进度: requestKey={}, lastUserId={}", requestKey, afterId);
                if (page.size() < BATCH_SIZE) break;
            }
        } else {
            if (userIds == null || userIds.isEmpty()) return false;
            List<Long> targets = new ArrayList<>(new LinkedHashSet<>(userIds));
            if (targets.stream().anyMatch(id -> id == null || id <= 0)) throw new BusinessException("用户 ID 无效");
            hadTargets = true;
            for (int i = 0; i < targets.size(); i += BATCH_SIZE) {
                processed += grantBatch(targets.subList(i, Math.min(i + BATCH_SIZE, targets.size())),
                        activityId, chances, requestKey);
            }
        }
        log.info("批量发放抽奖机会完成: requestKey={}, activityId={}, newlyApplied={}",
                requestKey, activityId, processed);
        return hadTargets;
    }

    /** 每页独立提交；同一请求重试只补齐尚未入账的用户。 */
    private int grantBatch(List<Long> ids, Long activityId, Integer chances, String requestKey) {
        return batchTransaction.execute(status -> {
            Long tenantId = TenantContext.getTenantId();
            chanceGrantMapper.claim(tenantId, requestKey, activityId, chances, ids);
            if (chanceGrantMapper.countConflicts(tenantId, requestKey, activityId, chances, ids) > 0) {
                throw new BusinessException("请求键已用于其他发放参数");
            }
            List<Long> pending = chanceGrantMapper.lockPending(tenantId, requestKey, ids);
            if (pending.isEmpty()) return 0;
            List<LotteryChanceGrantMapper.ChanceRow> rows = pending.stream()
                    .map(id -> new LotteryChanceGrantMapper.ChanceRow(IdWorker.getId(), id)).toList();
            chanceGrantMapper.apply(tenantId, activityId, chances, rows);
            if (chanceGrantMapper.markApplied(tenantId, requestKey, pending) != pending.size()) {
                throw new IllegalStateException("机会发放凭据更新数量不匹配");
            }
            return pending.size();
        });
    }

}
