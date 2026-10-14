package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.restaurant.DTO.DishCategory;
import org.hongtu.restaurant.DAO.DishCategoryMapper;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.service.DishCategoryService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.StoreInfoService;
import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <p>
 * 菜品分类表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
public class DishCategoryServiceImpl extends ServiceImpl<DishCategoryMapper, DishCategory> implements DishCategoryService {

    @Resource
    private StoreDataScopeService storeDataScopeService;

    @Resource
    private StoreInfoService storeInfoService;

    @Override
    public Page<DishCategory> getCategoryPage(Integer currentPage, Integer pageSize, String searchKey,
                                              Integer isShow, Integer level, Long storeId, Integer sourceType) {
        Long effectiveStoreId = storeId;
        if (effectiveStoreId != null) {
            storeDataScopeService.checkStoreAccess(effectiveStoreId);
        } else if (storeDataScopeService.requiresStoreRestriction()) {
            List<Long> stores = storeDataScopeService.getAllowedStoreIds();
            if (stores.size() != 1) throw new SecurityException("请选择当前有权限的门店");
            effectiveStoreId = stores.getFirst();
        }
        if (effectiveStoreId != null && storeInfoService.getById(effectiveStoreId) == null) {
            throw new IllegalArgumentException("门店不存在或不属于当前商户");
        }
        Page<DishCategory> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<DishCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DishCategory::getDeleted, 0)
                .like(StringUtils.hasText(searchKey), DishCategory::getCategoryName, searchKey)
                .eq(isShow != null && (isShow == 0 || isShow == 1), DishCategory::getIsShow, BooleanEnum.getByCode(isShow))
                .eq(level != null && (level == 1 || level == 2), DishCategory::getLevel, level)
                .eq(sourceType != null, DishCategory::getSourceType, sourceType);
        if (effectiveStoreId != null) {
            Long selectedStoreId = effectiveStoreId;
            queryWrapper.and(wrapper -> wrapper.eq(DishCategory::getSourceType, 0)
                    .or().eq(DishCategory::getOwnerStoreId, selectedStoreId)
                    .or(legacy -> legacy.isNull(DishCategory::getSourceType)
                            .and(item -> item.isNull(DishCategory::getStoreId)
                                    .or().eq(DishCategory::getStoreId, selectedStoreId))));
        }
        queryWrapper
                .orderByAsc(DishCategory::getSortOrder);
        return this.page(page, queryWrapper);
    }
}
