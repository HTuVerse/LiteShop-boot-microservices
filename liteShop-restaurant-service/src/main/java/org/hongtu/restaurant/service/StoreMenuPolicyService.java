package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.StoreMenuPolicyMapper;
import org.hongtu.restaurant.DTO.StoreMenuPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店菜单策略服务，为未单独配置的门店提供兼容性默认策略。
 */
@Service
public class StoreMenuPolicyService {

    @Resource
    private StoreMenuPolicyMapper policyMapper;

    @Resource
    private StoreDataScopeService storeDataScopeService;

    @Resource
    private StoreInfoService storeInfoService;

    /**
     * 获取门店策略；存量门店未配置时默认允许第一阶段的个性化能力。
     */
    public StoreMenuPolicy getEffectivePolicy(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("门店ID不能为空");
        }
        storeDataScopeService.checkStoreAccess(storeId);
        if (storeInfoService.getById(storeId) == null) {
            throw new IllegalArgumentException("门店不存在或不属于当前商户");
        }
        StoreMenuPolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<StoreMenuPolicy>()
                .eq(StoreMenuPolicy::getStoreId, storeId));
        if (policy == null) {
            return defaultPolicy(storeId);
        }
        normalize(policy);
        return policy;
    }

    /**
     * 保存门店策略。策略由总部账号维护，门店受限账号不能自行放宽权限。
     */
    @Transactional(rollbackFor = Exception.class)
    public StoreMenuPolicy savePolicy(StoreMenuPolicy request) {
        if (request == null || request.getStoreId() == null) {
            throw new IllegalArgumentException("门店ID不能为空");
        }
        if (storeDataScopeService.requiresStoreRestriction()) {
            throw new SecurityException("门店账号不能修改菜单策略");
        }
        if (storeInfoService.getById(request.getStoreId()) == null) {
            throw new IllegalArgumentException("门店不存在或不属于当前商户");
        }
        validateSwitch(request.getAllowLocalDish(), "门店自建菜品");
        validateSwitch(request.getAllowLocalCategory(), "门店自建分类");
        validateSwitch(request.getAllowCategoryOverride(), "门店调整菜品分类");
        validateSwitch(request.getAllowPriceOverride(), "门店覆盖价格");
        validateSwitch(request.getAllowStoreOffShelf(), "门店自主下架");
        validateSwitch(request.getAllowStoreExit(), "门店清退菜品");
        validateSwitch(request.getRequireApproval(), "菜单变更审批");
        normalize(request);
        validateRate(request.getMinPriceRate(), "最低售价比例");
        validateRate(request.getMaxPriceRate(), "最高售价比例");
        if (request.getMinPriceRate().compareTo(request.getMaxPriceRate()) > 0) {
            throw new BusinessException("最低售价比例不能大于最高售价比例");
        }

        StoreMenuPolicy existing = policyMapper.selectOne(new LambdaQueryWrapper<StoreMenuPolicy>()
                .eq(StoreMenuPolicy::getStoreId, request.getStoreId()));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            request.setId(null);
            request.setCreatedAt(now);
            request.setUpdatedAt(now);
            policyMapper.insert(request);
            return request;
        }
        request.setId(existing.getId());
        request.setCreatedAt(existing.getCreatedAt());
        request.setUpdatedAt(now);
        policyMapper.updateById(request);
        return request;
    }

    private StoreMenuPolicy defaultPolicy(Long storeId) {
        return new StoreMenuPolicy()
                .setStoreId(storeId)
                .setAllowLocalDish(1)
                .setAllowLocalCategory(1)
                .setAllowCategoryOverride(1)
                .setAllowPriceOverride(1)
                .setAllowStoreOffShelf(1)
                .setAllowStoreExit(1)
                .setMinPriceRate(BigDecimal.ZERO)
                .setMaxPriceRate(new BigDecimal("10.0000"))
                .setRequireApproval(0);
    }

    private void normalize(StoreMenuPolicy policy) {
        if (policy.getAllowLocalDish() == null) policy.setAllowLocalDish(1);
        if (policy.getAllowLocalCategory() == null) policy.setAllowLocalCategory(1);
        if (policy.getAllowCategoryOverride() == null) policy.setAllowCategoryOverride(1);
        if (policy.getAllowPriceOverride() == null) policy.setAllowPriceOverride(1);
        if (policy.getAllowStoreOffShelf() == null) policy.setAllowStoreOffShelf(1);
        if (policy.getAllowStoreExit() == null) policy.setAllowStoreExit(1);
        if (policy.getMinPriceRate() == null) policy.setMinPriceRate(BigDecimal.ZERO);
        if (policy.getMaxPriceRate() == null) policy.setMaxPriceRate(new BigDecimal("10.0000"));
        if (policy.getRequireApproval() == null) policy.setRequireApproval(0);
    }

    private void validateRate(BigDecimal rate, String name) {
        if (rate != null && rate.signum() < 0) {
            throw new BusinessException(name + "不能小于0");
        }
        if (rate != null && rate.compareTo(new BigDecimal("10.0000")) > 0) {
            throw new BusinessException(name + "不能大于10倍");
        }
    }

    private void validateSwitch(Integer value, String name) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException(name + "配置值只能是允许或禁止");
        }
    }
}
