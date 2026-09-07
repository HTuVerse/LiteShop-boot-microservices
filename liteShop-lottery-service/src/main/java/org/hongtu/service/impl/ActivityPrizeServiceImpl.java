package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.hongtu.DAO.ActivityPrizeMapper;
import org.hongtu.DAO.LotteryPrizeMapper;
import org.hongtu.DTO.ActivityPrize;
import org.hongtu.DTO.LotteryPrize;
import org.hongtu.Result.Result;
import org.hongtu.VO.ActivityLotteryPrizeVO;
import org.hongtu.VO.ActivityPrizeUserVO;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.service.ActivityPrizeService;
import org.hongtu.utils.BeanCopyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 活动奖品关联表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Service
public class ActivityPrizeServiceImpl extends ServiceImpl<ActivityPrizeMapper, ActivityPrize> implements ActivityPrizeService {

    @Resource
    ActivityPrizeMapper activityPrizeMapper;
    @Resource
    LotteryPrizeMapper lotteryPrizeMapper;


    @Override
    public Result<List<ActivityLotteryPrizeVO>> getActivityPrizes(Long activityId){
        // 1. 获取活动关联的奖品ID
        LambdaQueryWrapper<ActivityPrize> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivityPrize::getActivityId, activityId);
        List<ActivityPrize> activityPrizes = activityPrizeMapper.selectList(queryWrapper);
        if (activityPrizes.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 2. 获取奖品详情
        List<Long> prizeIds = activityPrizes.stream()
                .map(ActivityPrize::getPrizeId)
                .collect(Collectors.toList());
        LambdaQueryWrapper<LotteryPrize> prizeQueryWrapper = new LambdaQueryWrapper<>();
        prizeQueryWrapper.in(LotteryPrize::getId, prizeIds);
        List<LotteryPrize> prizes = lotteryPrizeMapper.selectList(prizeQueryWrapper);
        // 3. 创建库存映射
        Map<Long, ActivityPrize> prizeStockMap = activityPrizes.stream()
                .collect(Collectors.toMap(ActivityPrize::getPrizeId, activityPrize -> activityPrize));
        // 4. 使用BeanCopyUtils转换为VO对象
        List<ActivityLotteryPrizeVO> prizeVOList = BeanCopyUtils.copyBeanList(prizes, ActivityLotteryPrizeVO.class);
        // 5. 设置库存信息
        for (ActivityLotteryPrizeVO vo : prizeVOList) {
            ActivityPrize activityPrize = prizeStockMap.get(vo.getId());
            if (activityPrize != null) {
                vo.setTotalStock(activityPrize.getTotalStock());
                vo.setSurplusStock(activityPrize.getSurplusStock());
            }
        }
        return Result.success(prizeVOList);
    }

    @Override
    public Result<List<ActivityPrizeUserVO>> getActivityPrizesForUser(Long activityId) {
        // 1. 获取活动关联的奖品ID
        LambdaQueryWrapper<ActivityPrize> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivityPrize::getActivityId, activityId);
        List<ActivityPrize> activityPrizes = activityPrizeMapper.selectList(queryWrapper);
        if (activityPrizes.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        
        // 2. 获取奖品详情（只获取启用的奖品）
        List<Long> prizeIds = activityPrizes.stream()
                .map(ActivityPrize::getPrizeId)
                .collect(Collectors.toList());
        LambdaQueryWrapper<LotteryPrize> prizeQueryWrapper = new LambdaQueryWrapper<>();
        prizeQueryWrapper.in(LotteryPrize::getId, prizeIds)
                .eq(LotteryPrize::getEnabled, BooleanEnum.YES);
        List<LotteryPrize> prizes = lotteryPrizeMapper.selectList(prizeQueryWrapper);
        
        // 3. 创建库存映射
        Map<Long, ActivityPrize> prizeStockMap = activityPrizes.stream()
                .collect(Collectors.toMap(ActivityPrize::getPrizeId, activityPrize -> activityPrize));
        
        // 4. 转换为用户端VO（不包含敏感信息）
        List<ActivityPrizeUserVO> userVOList = new ArrayList<>();
        for (LotteryPrize prize : prizes) {
            ActivityPrizeUserVO vo = new ActivityPrizeUserVO();
            vo.setId(prize.getId());
            vo.setName(prize.getName());
            vo.setImgUrl(prize.getImgUrl());
            vo.setType(prize.getType());
            vo.setEnabled(prize.getEnabled());
            vo.setPoints(prize.getPoints());
            
            // 设置库存信息（只显示剩余库存）
            ActivityPrize activityPrize = prizeStockMap.get(prize.getId());
            if (activityPrize != null) {
                vo.setSurplusStock(activityPrize.getSurplusStock());
            }
            
            // 注意：不设置 probability 和 couponId 等敏感信息
            userVOList.add(vo);
        }
        
        return Result.success(userVOList);
    }

    @Override
    public Result<Boolean> addPrizeToActivity(ActivityPrize activityPrize){
        // 检查关联是否已存在
        LambdaQueryWrapper<ActivityPrize> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivityPrize::getActivityId, activityPrize.getActivityId())
                .eq(ActivityPrize::getPrizeId, activityPrize.getPrizeId());
        if (activityPrizeMapper.selectCount(queryWrapper) > 0) {
            return Result.error("该奖品已关联到活动");
        }else {
            activityPrize.setCreateTime(LocalDateTime.now());
            activityPrize.setTotalStock(activityPrize.getTotalStock());
            activityPrize.setSurplusStock(activityPrize.getTotalStock());
            int idNumber = activityPrizeMapper.insert(activityPrize);
            if (idNumber>0) {
                return Result.success(true);
            } else {
                return Result.error("添加奖品到活动失败");
            }
        }
    }

    @Override
    @Transactional
    public Result<Boolean> batchAddPrizesToActivity(Long activityId, List<Long> prizeIds){
        if (prizeIds == null || prizeIds.isEmpty()) {
            return Result.error("奖品列表不能为空");
        }

        List<ActivityPrize> activityPrizes = new ArrayList<>();
        for (Long prizeId : prizeIds) {
            ActivityPrize activityPrize = new ActivityPrize();
            activityPrize.setActivityId(activityId);
            activityPrize.setPrizeId(prizeId);
            activityPrize.setCreateTime(LocalDateTime.now());
            activityPrize.setTotalStock(0L);
            activityPrize.setSurplusStock(0L);
            activityPrizes.add(activityPrize);
        }

        boolean success = saveBatch(activityPrizes);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("批量添加奖品到活动失败");
        }
    }

    @Override
    public List<ActivityPrize> getInventoriesByActivityId(Long activityId) {
        LambdaQueryWrapper<ActivityPrize> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivityPrize::getActivityId, activityId);
        return list(queryWrapper);
    }

    @Override
    public Map<Long, Long> getPrizeInventoryMap(Long activityId) {
        List<ActivityPrize> inventories = getInventoriesByActivityId(activityId);
        Map<Long, Long> inventoryMap = new HashMap<>();
        for (ActivityPrize inventory : inventories) {
            inventoryMap.put(inventory.getPrizeId(), inventory.getSurplusStock());
        }
        return inventoryMap;
    }

}
