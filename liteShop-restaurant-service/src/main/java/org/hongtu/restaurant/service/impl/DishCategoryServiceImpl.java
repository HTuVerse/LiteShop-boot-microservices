package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.restaurant.DTO.DishCategory;
import org.hongtu.restaurant.DAO.DishCategoryMapper;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.service.DishCategoryService;
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

    @Override
    public Page<DishCategory> getCategoryPage(Integer currentPage, Integer pageSize, String searchKey, Integer isShow, Integer level) {
        Page<DishCategory> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<DishCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(searchKey), DishCategory::getCategoryName, searchKey)
                .eq(isShow != null && (isShow == 0 || isShow == 1), DishCategory::getIsShow, BooleanEnum.getByCode(isShow))
                .eq(level != null && (level == 1 || level == 2), DishCategory::getLevel, level)
                .orderByAsc(DishCategory::getSortOrder);
        return this.page(page, queryWrapper);
    }
}
