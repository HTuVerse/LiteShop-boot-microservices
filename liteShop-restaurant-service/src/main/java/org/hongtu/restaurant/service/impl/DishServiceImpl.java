package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DAO.DishMapper;
import org.hongtu.restaurant.DAO.DishSpecOptionMapper;
import org.hongtu.restaurant.DTO.DishCategory;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.VO.DishByCategoryVO;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.service.DishCategoryService;
import org.hongtu.restaurant.service.DishService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.service.DishSpecOptionService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.utils.BeanCopyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 菜品表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    @Resource
    DishSpecOptionService dishSpecOptionService;
    @Resource
    DishCategoryService dishCategoryService;
    @Resource
    StoreDataScopeService storeDataScopeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDish(Dish dish) {
        if (dish == null || dish.getStoreId() == null) {
            throw new IllegalArgumentException("菜品所属门店不能为空");
        }
        storeDataScopeService.checkStoreAccess(dish.getStoreId());
        validateCategoryStore(dish.getCategoryId(), dish.getStoreId());
        return baseMapper.insert(dish)> 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDish(Dish dish) {
        if (dish == null || dish.getId() == null) {
            throw new IllegalArgumentException("菜品ID不能为空");
        }
        Dish existing = baseMapper.selectById(dish.getId());
        if (existing == null) {
            return false;
        }
        Long targetStoreId = dish.getStoreId() == null ? existing.getStoreId() : dish.getStoreId();
        Long targetCategoryId = dish.getCategoryId() == null ? existing.getCategoryId() : dish.getCategoryId();
        storeDataScopeService.checkStoreAccess(existing.getStoreId());
        storeDataScopeService.checkStoreAccess(targetStoreId);
        validateCategoryStore(targetCategoryId, targetStoreId);
        dish.setUpdatedAt(LocalDateTime.now());

        LambdaUpdateWrapper<Dish> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Dish::getId, dish.getId());
        // 显式设置要更新的字段，包括可能为null的字段
        updateWrapper.set(Dish::getUpdatedAt, dish.getUpdatedAt());
        updateWrapper.set(Dish::getPrice, dish.getPrice()); // 即使为null也会更新
        return baseMapper.update(dish, updateWrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDish(Long id) {
        // 删除菜品
        return baseMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteDish(List<Long> ids) {
        // 批量删除菜品
        return baseMapper.deleteByIds(ids)> 0;
    }

    @Override
    public DishVO getDishDetail(Long id) {
        // 查询菜品详情
        Dish dish = baseMapper.selectById(id);
        if (dish == null) {
            return null;
        }
        DishVO dishVO = BeanCopyUtils.copyBean(dish, DishVO.class);
        // 查询菜品规格选项
        List<DishSpecOption> specOptions = dishSpecOptionService.selectSpecOptionsByDishId(id);
        dishVO.setSpecOptions(specOptions);
//        dishVO.setCategoryName();
        return dishVO;
    }

    @Override
    public Page<DishVO> getDishPage(Map<String, Object> params, Page<Dish> page) {
        // 使用MyBatis-Plus的分页查询
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        // 根据参数添加查询条件
        if (params != null) {
            // 门店ID查询
            Long storeId = (Long) params.get("storeId");
            if (storeId != null) {
                queryWrapper.eq(Dish::getStoreId, storeId);
            }
            // 分类ID查询
            Long categoryId = (Long) params.get("categoryId");
            if (categoryId != null) {
                queryWrapper.eq(Dish::getCategoryId, categoryId);
            }
            // 菜品名称模糊查询
            String dishName = (String) params.get("dishName");
            if (StringUtils.hasText(dishName)) {
                queryWrapper.like(Dish::getDishName, dishName);
            }
            // 是否推荐查询
            Integer isRecommend = (Integer) params.get("isRecommend");
            if (isRecommend != null) {
                queryWrapper.eq(Dish::getIsRecommend, isRecommend);
            }

            // 是否热门查询
            Integer isHot = (Integer) params.get("isHot");
            if (isHot != null) {
                queryWrapper.eq(Dish::getIsHot, isHot);
            }

            // 是否新品查询
            Integer isNew = (Integer) params.get("isNew");
            if (isNew != null) {
                queryWrapper.eq(Dish::getIsNew, isNew);
            }

            // 销售状态查询
            Integer saleStatus = (Integer) params.get("saleStatus");
            if (saleStatus != null) {
                queryWrapper.eq(Dish::getSaleStatus, saleStatus);
            }
            // 菜品场景查询
            Integer sceneType = (Integer) params.get("sceneType");
            if(sceneType != null){
                queryWrapper.eq(Dish::getSceneType, sceneType);
            }
        }
        // 默认按照排序值升序、ID降序排序
        queryWrapper.orderByAsc(Dish::getId).orderByAsc(Dish::getSortOrder);
        Page<Dish> dishPage = baseMapper.selectPage(page, queryWrapper);
        List<DishVO> dishVOList = dishPage.getRecords().stream().map(dish -> {
            DishVO dishVO = BeanCopyUtils.copyBean(dish, DishVO.class);
            List<DishSpecOption> specOptions = dishSpecOptionService.selectSpecOptionsByDishId(dish.getId());
            dishVO.setSpecOptions(specOptions);
            DishCategory category = dishCategoryService.getById(dish.getCategoryId());
            dishVO.setCategoryName(category == null ? null : category.getCategoryName());
            return dishVO;
        }).collect(Collectors.toList());

        // 创建VO分页对象
        Page<DishVO> dishVOPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        dishVOPage.setRecords(dishVOList);

        return dishVOPage;
    }


    @Override
    public Result listDishNoPage(String searchKey, Integer isListBySort, Integer isRecommend, Integer isHot, Integer isNew) {
        // 构建查询条件
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();

        // 如果有搜索关键字，进行模糊查询
        if (StringUtils.hasText(searchKey)) {
            queryWrapper.like(Dish::getDishName, searchKey);
        }

        // 是否推荐查询
        if (isRecommend != null) {
            queryWrapper.eq(Dish::getIsRecommend, isRecommend);
        }

        // 是否热门查询
        if (isHot != null) {
            queryWrapper.eq(Dish::getIsHot, isHot);
        }

        // 是否新品查询
        if (isNew != null) {
            queryWrapper.eq(Dish::getIsNew, isNew);
        }

        // 只查询上架状态的菜品（saleStatus = 1）
        queryWrapper.eq(Dish::getSaleStatus, 1);

        // 默认按排序值升序排序
        queryWrapper.orderByAsc(Dish::getSortOrder);

        // 查询菜品列表
        List<Dish> dishList = baseMapper.selectList(queryWrapper);

        // 获取所有分类ID，并批量查询分类信息
        Set<Long> categoryIds = dishList.stream()
                .map(Dish::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 批量查询分类信息并转换为Map
        Map<Long, DishCategory> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<DishCategory> categories = dishCategoryService.listByIds(categoryIds);
            categoryMap = categories.stream()
                    .collect(Collectors.toMap(DishCategory::getId, category -> category));
        }

        // 为了在lambda中使用，需要创建final引用
        final Map<Long, DishCategory> finalCategoryMap = categoryMap;

        // 转换为 DishVO 并填充分类名称和规格选项
        List<DishVO> dishVOList = dishList.stream().map(dish -> {
            DishVO dishVO = BeanCopyUtils.copyBean(dish, DishVO.class);

            // 查询并设置规格选项
            List<DishSpecOption> specOptions = dishSpecOptionService.selectSpecOptionsByDishId(dish.getId());
            dishVO.setSpecOptions(specOptions);

            // 查询并设置分类名称
            DishCategory category = finalCategoryMap.get(dish.getCategoryId());
            if (category != null) {
                dishVO.setCategoryName(category.getCategoryName());
            }

            return dishVO;
        }).collect(Collectors.toList());

        // 根据 isListBySort 参数决定返回格式
        if (isListBySort != null && isListBySort == 1) {
            // 按分类ID分组返回
            Map<Long, List<DishVO>> dishGroupMap = dishVOList.stream()
                    .collect(Collectors.groupingBy(
                            dishVO -> dishVO.getCategoryId() != null ? dishVO.getCategoryId() : 0L,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            // 查询所有分类（按排序值升序），确保无菜品的分类也会返回
            LambdaQueryWrapper<DishCategory> categoryQuery = new LambdaQueryWrapper<>();
            categoryQuery.orderByAsc(DishCategory::getSortOrder);
            List<DishCategory> allCategories = dishCategoryService.list(categoryQuery);

            // 转换为 DishByCategoryVO 列表，遍历所有分类，无菜品的分类 dishes 为空列表
            List<DishByCategoryVO> result = allCategories.stream()
                    .map(category -> {
                        Long categoryId = category.getId();
                        List<DishVO> dishes = dishGroupMap.getOrDefault(categoryId, Collections.emptyList());
                        return new DishByCategoryVO(
                                category.getCategoryName(),
                                category.getCategoryDesc() != null ? category.getCategoryDesc() : "",
                                category.getCategoryIcon() != null ? category.getCategoryIcon() : "",
                                dishes
                        );
                    })
                    .collect(Collectors.toList());

            // 处理 categoryId 为 null 的菜品（未分类），追加到结果末尾
            List<DishVO> uncategorizedDishes = dishGroupMap.get(0L);
            if (uncategorizedDishes != null && !uncategorizedDishes.isEmpty()) {
                result.add(new DishByCategoryVO("未分类", "", "", uncategorizedDishes));
            }

            return Result.success(result);
        } else {
            // 直接返回菜品列表
            return Result.success(dishVOList);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean decreaseStock(Long dishId, Integer quantity) {
        if (dishId == null || quantity == null || quantity <= 0) {
            return false;
        }

        // 执行库存减少操作（SQL中会检查库存是否充足）
        int affectedRows = baseMapper.decreaseStock(dishId, quantity);

        // 如果影响行数为0，说明库存不足
        if (affectedRows == 0) {
            // 查询当前库存
            Dish dish = baseMapper.selectById(dishId);
            if (dish != null) {
                throw new RuntimeException("菜品【" + dish.getDishName() + "】库存不足，当前库存：" + dish.getStock() + "，需要：" + quantity);
            } else {
                throw new RuntimeException("菜品不存在，ID：" + dishId);
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDecreaseStock(Map<Long, Integer> dishStockMap) {
        if (dishStockMap == null || dishStockMap.isEmpty()) {
            return true;
        }

        // 遍历每个菜品进行库存减少
        for (Map.Entry<Long, Integer> entry : dishStockMap.entrySet()) {
            Long dishId = entry.getKey();
            Integer quantity = entry.getValue();

            // 调用单个菜品的库存减少方法
            decreaseStock(dishId, quantity);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchIncreaseStockAndDecreaseSales(Map<Long, Integer> dishQuantityMap) {
        if (dishQuantityMap == null || dishQuantityMap.isEmpty()) {
            return true;
        }
        for (Map.Entry<Long, Integer> entry : dishQuantityMap.entrySet()) {
            Long dishId = entry.getKey();
            Integer quantity = entry.getValue();
            if (dishId == null || quantity == null || quantity <= 0) {
                continue;
            }
            baseMapper.increaseStockAndDecreaseSales(dishId, quantity);
        }
        return true;
    }

    private void validateCategoryStore(Long categoryId, Long storeId) {
        if (categoryId == null) {
            return;
        }
        DishCategory category = dishCategoryService.getById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("菜品分类不存在");
        }
        if (!Objects.equals(category.getStoreId(), storeId)) {
            throw new IllegalArgumentException("菜品与分类必须属于同一门店");
        }
    }

/*
    @Override
    public boolean updateDishSalesCount(Long id, Integer count) {
        return dishMapper.updateDishSalesCount(id, count) > 0;
    }*/

}
