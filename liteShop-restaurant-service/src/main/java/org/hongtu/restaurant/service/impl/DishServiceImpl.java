package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DAO.DishMapper;
import org.hongtu.restaurant.DAO.DishSpecOptionMapper;
import org.hongtu.restaurant.DAO.StoreDishMapper;
import org.hongtu.restaurant.DTO.DishCategory;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.DTO.StoreDish;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.StoreMenuPolicy;
import org.hongtu.restaurant.VO.DishByCategoryVO;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.VO.StoreDishConfigRequest;
import org.hongtu.restaurant.service.DishCategoryService;
import org.hongtu.restaurant.service.DishService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.service.DishSpecOptionService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.StoreInfoService;
import org.hongtu.restaurant.service.StoreMenuPolicyService;
import org.hongtu.restaurant.service.PricingService;
import org.hongtu.utils.BeanCopyUtils;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.OnOrOffShelfEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
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
    @Resource
    StoreDishMapper storeDishMapper;
    @Resource
    StoreInfoService storeInfoService;
    @Resource
    StoreMenuPolicyService storeMenuPolicyService;
    @Resource
    PricingService pricingService;
    @Resource
    ResourceBindingCoordinator resourceBindings;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDish(Dish dish) {
        if (dish == null) {
            throw new IllegalArgumentException("菜品信息不能为空");
        }
        boolean restricted = storeDataScopeService.requiresStoreRestriction();
        int sourceType = dish.getSourceType() == null
                ? (dish.getStoreId() == null && !restricted ? 0 : 1)
                : dish.getSourceType();
        if (sourceType != 0 && sourceType != 1) {
            throw new IllegalArgumentException("菜品来源不正确");
        }

        Long ownerStoreId = sourceType == 1
                ? (dish.getOwnerStoreId() == null ? dish.getStoreId() : dish.getOwnerStoreId())
                : null;
        if (sourceType == 0 && restricted) {
            throw new SecurityException("门店账号不能创建总部统一菜品");
        }
        if (sourceType == 1) {
            if (ownerStoreId == null) {
                throw new IllegalArgumentException("门店自建菜品必须选择所属门店");
            }
            storeDataScopeService.checkStoreAccess(ownerStoreId);
            StoreMenuPolicy policy = storeMenuPolicyService.getEffectivePolicy(ownerStoreId);
            if (restricted && !enabled(policy.getAllowLocalDish())) {
                throw new BusinessException("总部未允许该门店自建菜品");
            }
        }

        validateCategoryScope(dish.getCategoryId(), sourceType, ownerStoreId);
        dish.setId(dish.getId() == null ? IdWorker.getId() : dish.getId());
        ResourceBindingPlan imagePlan = resourceBindings.prepareMulti("DISH", dish.getId(), "dishPics",
                dish.getDishPicResourceIds(), dish.getDishPics());
        dish.setDishPics(resourceBindings.resolvedMulti(imagePlan, dish.getDishPics()));
        dish.setDishCode(StringUtils.hasText(dish.getDishCode())
                ? dish.getDishCode().trim() : "D" + dish.getId());
        dish.setSourceType(sourceType);
        dish.setOwnerStoreId(ownerStoreId);
        // 旧字段仅为迁移期兼容：总部菜为空，门店自建菜保留所属门店。
        dish.setStoreId(ownerStoreId);
        dish.setMasterStatus(dish.getMasterStatus() == null
                ? (dish.getSaleStatus() == OnOrOffShelfEnum.OFF_SHELF ? 2 : 1)
                : dish.getMasterStatus());
        dish.setVersion(1);
        dish.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        if (dish.getCreatedAt() == null) dish.setCreatedAt(now);
        dish.setUpdatedAt(now);
        if (baseMapper.insert(dish) <= 0) {
            return false;
        }
        resourceBindings.completeAfterCommit(imagePlan);

        // 第一阶段的总部菜品默认统一发布到当前租户全部门店；门店可按策略下架或清退。
        List<Long> targetStoreIds = sourceType == 0
                ? resolveTargetStores(null) : List.of(ownerStoreId);
        for (Long storeId : targetStoreIds) {
            createStoreBindingIfMissing(dish, storeId);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDish(Dish dish) {
        if (dish == null || dish.getId() == null) {
            throw new IllegalArgumentException("菜品ID不能为空");
        }
        Dish existing = baseMapper.selectById(dish.getId());
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
            return false;
        }
        int sourceType = normalizedSourceType(existing);
        Long ownerStoreId = sourceType == 1
                ? (existing.getOwnerStoreId() == null ? existing.getStoreId() : existing.getOwnerStoreId())
                : null;
        if (sourceType == 0 && storeDataScopeService.requiresStoreRestriction()) {
            throw new SecurityException("门店账号只能修改总部菜品的门店配置");
        }
        if (sourceType == 1) {
            storeDataScopeService.checkStoreAccess(ownerStoreId);
        }
        Long targetCategoryId = dish.getCategoryId() == null ? existing.getCategoryId() : dish.getCategoryId();
        if (sourceType == 1 && storeDataScopeService.requiresStoreRestriction()
                && !Objects.equals(targetCategoryId, existing.getCategoryId())
                && !enabled(storeMenuPolicyService.getEffectivePolicy(ownerStoreId)
                .getAllowCategoryOverride())) {
            throw new BusinessException("总部未允许该门店调整菜品分类");
        }
        validateCategoryScope(targetCategoryId, sourceType, ownerStoreId);
        if (dish.getDishPics() == null) dish.setDishPics(existing.getDishPics());
        if (dish.getDishPicResourceIds() == null)
            dish.setDishPicResourceIds(Objects.equals(dish.getDishPics(), existing.getDishPics())
                    ? resourceBindings.currentMultiIds("DISH", dish.getId(), "dishPics", existing.getDishPics()) : "");
        ResourceBindingPlan imagePlan = resourceBindings.prepareMulti("DISH", dish.getId(), "dishPics",
                dish.getDishPicResourceIds(), dish.getDishPics());
        dish.setDishPics(resourceBindings.resolvedMulti(imagePlan, dish.getDishPics()));
        dish.setSourceType(sourceType);
        dish.setOwnerStoreId(ownerStoreId);
        dish.setStoreId(ownerStoreId);
        dish.setDishCode(existing.getDishCode());
        dish.setDeleted(existing.getDeleted());
        dish.setMasterStatus(dish.getSaleStatus() == null
                ? defaultInt(existing.getMasterStatus(), 1)
                : (dish.getSaleStatus() == OnOrOffShelfEnum.ON_SHELF ? 1 : 2));
        dish.setVersion((existing.getVersion() == null ? 1 : existing.getVersion()) + 1);
        dish.setUpdatedAt(LocalDateTime.now());

        LambdaUpdateWrapper<Dish> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Dish::getId, dish.getId());
        // 显式设置要更新的字段，包括可能为null的字段
        updateWrapper.set(Dish::getUpdatedAt, dish.getUpdatedAt());
        updateWrapper.set(Dish::getSalePrice, dish.getSalePrice());
        int updated = baseMapper.update(dish, updateWrapper);
        if (updated > 0) resourceBindings.completeAfterCommit(imagePlan);
        if (updated > 0 && sourceType == 0) {
            // 总部主数据动态继承，门店个性化覆盖不受影响，只推进继承版本。
            storeDishMapper.update(null, new LambdaUpdateWrapper<StoreDish>()
                    .eq(StoreDish::getDishId, dish.getId())
                    .eq(StoreDish::getDeleted, 0)
                    .set(StoreDish::getInheritedVersion, dish.getVersion())
                    .set(StoreDish::getUpdatedAt, LocalDateTime.now()));
        }
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDish(Long id) {
        Dish existing = baseMapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) return false;
        int sourceType = normalizedSourceType(existing);
        if (sourceType == 0 && storeDataScopeService.requiresStoreRestriction()) {
            throw new SecurityException("门店不能删除总部菜品，请使用门店清退");
        }
        if (sourceType == 1) {
            storeDataScopeService.checkStoreAccess(ownerStore(existing));
        }
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, id)
                .set(Dish::getDeleted, 1)
                .set(Dish::getMasterStatus, 2)
                .set(Dish::getUpdatedAt, LocalDateTime.now()));
        if (updated > 0) resourceBindings.clearAfterCommit("DISH", id, "dishPics");
        if (updated > 0) {
            storeDishMapper.update(null, new LambdaUpdateWrapper<StoreDish>()
                    .eq(StoreDish::getDishId, id)
                    .set(StoreDish::getDeleted, 1)
                    .set(StoreDish::getUpdatedAt, LocalDateTime.now()));
        }
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteDish(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return false;
        boolean changed = false;
        for (Long id : ids) changed |= deleteDish(id);
        return changed;
    }

    @Override
    public DishVO getDishDetail(Long id) {
        // 查询菜品详情
        Dish dish = baseMapper.selectById(id);
        if (dish == null || Integer.valueOf(1).equals(dish.getDeleted())) {
            return null;
        }
        Long scopedStoreId = inferredStoreId(null);
        if (scopedStoreId != null) {
            return getEffectiveDish(id, scopedStoreId, false);
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
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        Long storeId = inferredStoreId((Long) safeParams.get("storeId"));
        if (storeId != null) {
            return getStoreDishPage(safeParams, page, storeId);
        }

        // 未选择门店时展示总部主数据；门店受限账号会在上方自动落到当前门店。
        LambdaQueryWrapper<Dish> wrapper = masterQuery(safeParams);
        Integer sourceType = (Integer) safeParams.get("sourceType");
        wrapper.eq(sourceType != null, Dish::getSourceType, sourceType)
                .orderByAsc(Dish::getSortOrder)
                .orderByDesc(Dish::getId);
        Page<Dish> dishPage = baseMapper.selectPage(page, wrapper);
        List<DishVO> records = dishPage.getRecords().stream()
                .map(this::toMasterVO)
                .toList();
        Page<DishVO> result = new Page<>(dishPage.getCurrent(), dishPage.getSize(), dishPage.getTotal());
        result.setRecords(records);
        return result;
    }


    @Override
    public Result listDishNoPage(Long storeId, String searchKey, Integer isListBySort,
                                 Integer isRecommend, Integer isHot, Integer isNew) {
        Long effectiveStoreId = inferredStoreId(storeId);
        if (effectiveStoreId == null) {
            List<StoreInfo> stores = storeInfoService.list();
            if (stores.size() != 1) {
                throw new IllegalArgumentException("连锁商户查询点餐菜单时必须指定门店");
            }
            effectiveStoreId = stores.getFirst().getId();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("storeId", effectiveStoreId);
        params.put("dishName", searchKey);
        params.put("isRecommend", isRecommend);
        params.put("isHot", isHot);
        params.put("isNew", isNew);
        params.put("saleStatus", 1);
        Page<DishVO> page = getStoreDishPage(params, new Page<>(1, 10_000), effectiveStoreId);
        List<DishVO> dishVOList = page.getRecords().stream()
                .filter(item -> !Integer.valueOf(1).equals(item.getSoldOut()))
                .toList();

        // 根据 isListBySort 参数决定返回格式
        if (isListBySort != null && isListBySort == 1) {
            // 按分类ID分组返回
            Map<Long, List<DishVO>> dishGroupMap = dishVOList.stream()
                    .collect(Collectors.groupingBy(
                            dishVO -> dishVO.getCategoryId() != null ? dishVO.getCategoryId() : 0L,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            Map<Long, DishCategory> categories = categoryMap(dishVOList.stream()
                    .map(DishVO::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet()));
            List<DishByCategoryVO> result = dishGroupMap.entrySet().stream()
                    .filter(entry -> entry.getKey() != 0L)
                    .map(entry -> {
                        DishCategory category = categories.get(entry.getKey());
                        return new DishByCategoryVO(
                                category == null ? "未分类" : category.getCategoryName(),
                                category == null || category.getCategoryDesc() == null ? "" : category.getCategoryDesc(),
                                category == null || category.getCategoryIcon() == null ? "" : category.getCategoryIcon(),
                                entry.getValue(), entry.getKey());
                    }).collect(Collectors.toList());

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
        Dish dish = baseMapper.selectById(dishId);
        Long storeId = dish == null ? null : ownerStore(dish);
        if (storeId == null) {
            throw new BusinessException("总部菜品扣减库存时必须指定门店");
        }
        return decreaseStock(storeId, dishId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean decreaseStock(Long storeId, Long dishId, Integer quantity) {
        if (storeId == null || dishId == null || quantity == null || quantity <= 0) return false;
        getEffectiveDish(dishId, storeId, true);
        if (storeDishMapper.decreaseStock(storeId, dishId, quantity) == 0) {
            StoreDish config = findStoreDish(storeId, dishId);
            throw new BusinessException(config == null
                    ? "该门店未配置此菜品"
                    : "菜品库存不足，当前库存：" + config.getStock() + "，需要：" + quantity);
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
    public boolean batchDecreaseStock(Long storeId, Map<Long, Integer> dishStockMap) {
        if (dishStockMap == null || dishStockMap.isEmpty()) return true;
        for (Map.Entry<Long, Integer> entry : dishStockMap.entrySet()) {
            decreaseStock(storeId, entry.getKey(), entry.getValue());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchReserveStock(Long storeId, Map<Long, Integer> dishStockMap) {
        return changeStoreStock(storeId, dishStockMap, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchCommitSales(Long storeId, Map<Long, Integer> dishStockMap) {
        if (dishStockMap == null || dishStockMap.isEmpty()) return true;
        storeDataScopeService.checkStoreAccess(storeId);
        for (Map.Entry<Long, Integer> entry : dishStockMap.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0
                    && storeDishMapper.commitSales(storeId, entry.getKey(), entry.getValue()) == 0) {
                throw new BusinessException("计入菜品销量失败");
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchReleaseReservedStock(Long storeId, Map<Long, Integer> dishStockMap) {
        if (dishStockMap == null || dishStockMap.isEmpty()) return true;
        storeDataScopeService.checkStoreAccess(storeId);
        for (Map.Entry<Long, Integer> entry : dishStockMap.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                storeDishMapper.releaseReservedStock(storeId, entry.getKey(), entry.getValue());
            }
        }
        return true;
    }

    private boolean changeStoreStock(Long storeId, Map<Long, Integer> dishStockMap, boolean reserve) {
        if (dishStockMap == null || dishStockMap.isEmpty()) return true;
        for (Map.Entry<Long, Integer> entry : dishStockMap.entrySet()) {
            Long dishId = entry.getKey();
            Integer quantity = entry.getValue();
            if (storeId == null || dishId == null || quantity == null || quantity <= 0) return false;
            getEffectiveDish(dishId, storeId, true);
            int updated = reserve
                    ? storeDishMapper.reserveStock(storeId, dishId, quantity)
                    : storeDishMapper.decreaseStock(storeId, dishId, quantity);
            if (updated == 0) {
                StoreDish config = findStoreDish(storeId, dishId);
                throw new BusinessException(config == null
                        ? "该门店未配置此菜品"
                        : "菜品库存不足，当前库存：" + config.getStock() + "，需要：" + quantity);
            }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchIncreaseStockAndDecreaseSales(Long storeId, Map<Long, Integer> dishQuantityMap) {
        if (dishQuantityMap == null || dishQuantityMap.isEmpty()) return true;
        storeDataScopeService.checkStoreAccess(storeId);
        for (Map.Entry<Long, Integer> entry : dishQuantityMap.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                storeDishMapper.restoreStock(storeId, entry.getKey(), entry.getValue());
            }
        }
        return true;
    }

    @Override
    public DishVO getEffectiveDish(Long dishId, Long storeId, boolean requireSaleable) {
        if (dishId == null || storeId == null) throw new IllegalArgumentException("门店和菜品不能为空");
        requireStore(storeId);
        ensureStoreBindings(storeId);
        Dish dish = baseMapper.selectById(dishId);
        StoreDish config = findStoreDish(storeId, dishId);
        if (dish == null || config == null || Integer.valueOf(1).equals(dish.getDeleted())
                || Integer.valueOf(1).equals(config.getDeleted())) {
            throw new BusinessException("该门店不存在此菜品");
        }
        if (requireSaleable && (!Integer.valueOf(1).equals(defaultInt(dish.getMasterStatus(), 1))
                || !Integer.valueOf(1).equals(config.getListingStatus())
                || Integer.valueOf(1).equals(config.getSoldOut()))) {
            throw new BusinessException("菜品【" + dish.getDishName() + "】当前不可售");
        }
        return toEffectiveVO(dish, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DishVO updateStoreDishConfig(StoreDishConfigRequest request) {
        validateStoreDishConfig(request);
        Long storeId = request.getStoreId();
        Dish dish = baseMapper.selectById(request.getDishId());
        StoreDish existing = findStoreDish(storeId, request.getDishId());

        LambdaUpdateWrapper<StoreDish> update = new LambdaUpdateWrapper<StoreDish>()
                .eq(StoreDish::getId, existing.getId())
                .eq(request.getExpectedConfigVersion() != null, StoreDish::getConfigVersion,
                        request.getExpectedConfigVersion())
                // 覆盖字段允许显式传 null，以便恢复继承总部配置。
                .set(StoreDish::getCategoryOverrideId, request.getCategoryOverrideId())
                .set(StoreDish::getSalePriceOverride, request.getSalePriceOverride())
                .set(StoreDish::getCostPriceOverride, request.getCostPriceOverride())
                .set(request.getListingStatus() != null, StoreDish::getListingStatus, request.getListingStatus())
                .set(request.getSoldOut() != null, StoreDish::getSoldOut, request.getSoldOut())
                .set(request.getInventoryMode() != null, StoreDish::getInventoryMode, request.getInventoryMode())
                .set(request.getStock() != null, StoreDish::getStock, request.getStock())
                .set(StoreDish::getRecommendOverride, request.getRecommendOverride())
                .set(StoreDish::getHotOverride, request.getHotOverride())
                .set(StoreDish::getNewOverride, request.getNewOverride())
                .set(StoreDish::getSortOrderOverride, request.getSortOrderOverride())
                .set(StoreDish::getInheritedVersion, defaultInt(dish.getVersion(), 1))
                .set(StoreDish::getConfigVersion, defaultInt(existing.getConfigVersion(), 1) + 1)
                .set(StoreDish::getUpdatedAt, LocalDateTime.now());
        if (storeDishMapper.update(null, update) == 0) {
            throw new BusinessException("门店菜品配置已发生变化，请刷新后重新提交");
        }
        return getEffectiveDish(dish.getId(), storeId, false);
    }

    @Override
    public void validateStoreDishConfig(StoreDishConfigRequest request) {
        if (request == null || request.getStoreId() == null || request.getDishId() == null) {
            throw new IllegalArgumentException("门店和菜品不能为空");
        }
        Long storeId = request.getStoreId();
        requireStore(storeId);
        ensureStoreBindings(storeId);
        Dish dish = baseMapper.selectById(request.getDishId());
        StoreDish existing = findStoreDish(storeId, request.getDishId());
        if (dish == null || existing == null || Integer.valueOf(1).equals(dish.getDeleted())) {
            throw new BusinessException("该门店不存在此菜品");
        }

        boolean restricted = storeDataScopeService.requiresStoreRestriction();
        StoreMenuPolicy policy = storeMenuPolicyService.getEffectivePolicy(storeId);
        boolean salePriceChanged = !sameDecimal(existing.getSalePriceOverride(), request.getSalePriceOverride());
        boolean costPriceChanged = !sameDecimal(existing.getCostPriceOverride(), request.getCostPriceOverride());
        boolean addsOrChangesPriceOverride = (salePriceChanged && request.getSalePriceOverride() != null)
                || (costPriceChanged && request.getCostPriceOverride() != null);
        boolean listingStatusChanged = request.getListingStatus() != null
                && !Objects.equals(existing.getListingStatus(), request.getListingStatus());
        boolean categoryChanged = !Objects.equals(existing.getCategoryOverrideId(),
                request.getCategoryOverrideId());
        if (restricted && addsOrChangesPriceOverride && !enabled(policy.getAllowPriceOverride())) {
            throw new BusinessException("总部未允许该门店修改菜品价格");
        }
        if (restricted && listingStatusChanged && Integer.valueOf(0).equals(request.getListingStatus())
                && !enabled(policy.getAllowStoreOffShelf())) {
            throw new BusinessException("总部未允许该门店自主下架菜品");
        }
        if (restricted && listingStatusChanged && Integer.valueOf(2).equals(request.getListingStatus())
                && !enabled(policy.getAllowStoreExit())) {
            throw new BusinessException("总部未允许该门店清退菜品");
        }
        if (restricted && categoryChanged && request.getCategoryOverrideId() != null
                && !enabled(policy.getAllowCategoryOverride())) {
            throw new BusinessException("总部未允许该门店调整菜品分类");
        }
        // 售价区间只约束门店主动改价；总部发布以及未改价的库存/售罄操作不应被误拦截。
        if (restricted && salePriceChanged) {
            validatePriceOverride(dish, request.getSalePriceOverride(), policy);
        }
        if (request.getCategoryOverrideId() != null) {
            validateCategoryScope(request.getCategoryOverrideId(), 1, storeId);
        }
        if (request.getListingStatus() != null && (request.getListingStatus() < 0 || request.getListingStatus() > 2)) {
            throw new IllegalArgumentException("门店销售状态不正确");
        }
        if (request.getStock() != null && request.getStock() < 0) {
            throw new IllegalArgumentException("库存不能小于0");
        }
    }

    private Page<DishVO> getStoreDishPage(Map<String, Object> params, Page<Dish> page, Long storeId) {
        requireStore(storeId);
        ensureStoreBindings(storeId);
        Page<StoreDish> configPage = new Page<>(page.getCurrent(), page.getSize());
        storeDishMapper.selectStoreDishPage(configPage, TenantContext.getTenantId(), storeId, params);
        if (configPage.getRecords().isEmpty()) {
            Page<DishVO> empty = new Page<>(configPage.getCurrent(), configPage.getSize(), configPage.getTotal());
            empty.setRecords(List.of());
            return empty;
        }
        Map<Long, Dish> dishes = baseMapper.selectBatchIds(configPage.getRecords().stream()
                        .map(StoreDish::getDishId).toList()).stream()
                .collect(Collectors.toMap(Dish::getId, item -> item));
        List<DishVO> records = configPage.getRecords().stream()
                .filter(config -> dishes.containsKey(config.getDishId()))
                .map(config -> toEffectiveVO(dishes.get(config.getDishId()), config, false, false))
                .toList();
        if (records.isEmpty()) {
            Page<DishVO> empty = new Page<>(configPage.getCurrent(), configPage.getSize(), configPage.getTotal());
            empty.setRecords(List.of());
            return empty;
        }
        // 规格和分类按本页 ID 批量装载，避免每行两次明细查询。
        List<Long> recordIds = records.stream().map(DishVO::getId).toList();
        Map<Long, List<DishSpecOption>> optionsByDish = dishSpecOptionService.list(
                new LambdaQueryWrapper<DishSpecOption>().in(DishSpecOption::getDishId, recordIds)
                        .orderByAsc(DishSpecOption::getSortOrder)).stream()
                .collect(Collectors.groupingBy(DishSpecOption::getDishId));
        Map<Long, DishCategory> categories = categoryMap(records.stream()
                .map(DishVO::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (DishVO record : records) {
            record.setSpecOptions(optionsByDish.getOrDefault(record.getId(), List.of()));
            DishCategory category = categories.get(record.getCategoryId());
            record.setCategoryName(category == null ? null : category.getCategoryName());
        }
        pricingService.enrichMenuPrices(records, storeId);
        Page<DishVO> result = new Page<>(configPage.getCurrent(), configPage.getSize(), configPage.getTotal());
        result.setRecords(records);
        return result;
    }

    private LambdaQueryWrapper<Dish> masterQuery(Map<String, Object> params) {
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .eq(Dish::getDeleted, 0);
        Long categoryId = (Long) params.get("categoryId");
        String dishName = (String) params.get("dishName");
        Integer isRecommend = (Integer) params.get("isRecommend");
        Integer isHot = (Integer) params.get("isHot");
        Integer isNew = (Integer) params.get("isNew");
        Integer sceneType = (Integer) params.get("sceneType");
        Integer sourceType = (Integer) params.get("sourceType");
        return wrapper.eq(categoryId != null, Dish::getCategoryId, categoryId)
                .like(StringUtils.hasText(dishName), Dish::getDishName, dishName)
                .eq(isRecommend != null, Dish::getIsRecommend, isRecommend)
                .eq(isHot != null, Dish::getIsHot, isHot)
                .eq(isNew != null, Dish::getIsNew, isNew)
                .eq(sourceType != null, Dish::getSourceType, sourceType)
                .eq(sceneType != null, Dish::getSceneType, sceneType);
    }

    /** 为新门店和存量数据补齐总部菜品关联，保证总部新增门店后无需复制菜品。 */
    private void ensureStoreBindings(Long storeId) {
        Long tenantId = TenantContext.getTenantId();
        while (true) {
            List<Dish> missing = baseMapper.selectMissingStoreBindings(tenantId, storeId, 200);
            if (missing.isEmpty()) return;
            List<StoreDish> rows = missing.stream().map(dish -> new StoreDish()
                    .setId(IdWorker.getId())
                    .setStoreId(storeId)
                    .setDishId(dish.getId())
                    .setListingStatus(dish.getSaleStatus() == null ? 1 : dish.getSaleStatus().getCode())
                    .setSoldOut(0)
                    .setInventoryMode(1)
                    .setStock(defaultInt(dish.getStock(), 0))
                    .setSalesCount(defaultInt(dish.getSalesCount(), 0))
                    .setInheritedVersion(defaultInt(dish.getVersion(), 1))
                    .setConfigVersion(1)
                    .setDeleted(0)
                    .setCreatedAt(LocalDateTime.now())
                    .setUpdatedAt(LocalDateTime.now())).toList();
            storeDishMapper.insertMissingBindings(tenantId, rows);
            if (missing.size() < 200) return;
        }
    }

    private void createStoreBindingIfMissing(Dish dish, Long storeId) {
        if (findStoreDish(storeId, dish.getId()) != null) return;
        StoreDish config = new StoreDish()
                .setStoreId(storeId)
                .setDishId(dish.getId())
                .setListingStatus(dish.getSaleStatus() == null ? 1 : dish.getSaleStatus().getCode())
                .setSoldOut(0)
                .setInventoryMode(1)
                .setStock(defaultInt(dish.getStock(), 0))
                .setSalesCount(defaultInt(dish.getSalesCount(), 0))
                .setInheritedVersion(defaultInt(dish.getVersion(), 1))
                .setConfigVersion(1)
                .setDeleted(0)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        try {
            storeDishMapper.insert(config);
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // 并发首次访问同一门店时，唯一键保证只创建一条关联。
        }
    }

    private StoreDish findStoreDish(Long storeId, Long dishId) {
        return storeDishMapper.selectOne(new LambdaQueryWrapper<StoreDish>()
                .eq(StoreDish::getStoreId, storeId)
                .eq(StoreDish::getDishId, dishId));
    }

    private DishVO toMasterVO(Dish dish) {
        DishVO vo = BeanCopyUtils.copyBean(dish, DishVO.class);
        vo.setEffectiveSalePrice(dish.getSalePrice());
        vo.setDisplayPrice(dish.getSalePrice());
        vo.setPriceSource("HEADQUARTERS");
        vo.setListingStatus(dish.getSaleStatus() == null ? 1 : dish.getSaleStatus().getCode());
        vo.setSalePriceOverridden(false);
        fillDetails(vo);
        return vo;
    }

    private DishVO toEffectiveVO(Dish dish, StoreDish config) {
        return toEffectiveVO(dish, config, true);
    }

    private DishVO toEffectiveVO(Dish dish, StoreDish config, boolean withDetails) {
        return toEffectiveVO(dish, config, withDetails, true);
    }

    private DishVO toEffectiveVO(Dish dish, StoreDish config, boolean withDetails, boolean withPricing) {
        DishVO vo = BeanCopyUtils.copyBean(dish, DishVO.class);
        vo.setStoreDishId(config.getId());
        vo.setStoreId(config.getStoreId());
        vo.setCategoryOverrideId(config.getCategoryOverrideId());
        vo.setSalePriceOverride(config.getSalePriceOverride());
        vo.setCostPriceOverride(config.getCostPriceOverride());
        BigDecimal effectiveSalePrice = config.getSalePriceOverride() == null
                ? dish.getSalePrice() : config.getSalePriceOverride();
        vo.setEffectiveSalePrice(effectiveSalePrice);
        vo.setDisplayPrice(effectiveSalePrice);
        vo.setPriceSource(config.getSalePriceOverride() == null ? "HEADQUARTERS" : "STORE_OVERRIDE");
        if (config.getCostPriceOverride() != null) vo.setCostPrice(config.getCostPriceOverride());
        if (config.getCategoryOverrideId() != null) vo.setCategoryId(config.getCategoryOverrideId());
        vo.setStoreListingStatus(config.getListingStatus());
        int effectiveListingStatus = Integer.valueOf(1).equals(defaultInt(dish.getMasterStatus(), 1))
                ? defaultInt(config.getListingStatus(), 0) : 0;
        vo.setListingStatus(effectiveListingStatus);
        vo.setSaleStatus(effectiveListingStatus == 1
                ? OnOrOffShelfEnum.ON_SHELF : OnOrOffShelfEnum.OFF_SHELF);
        vo.setSoldOut(config.getSoldOut());
        vo.setInventoryMode(config.getInventoryMode());
        vo.setStock(config.getStock());
        vo.setSalesCount(config.getSalesCount());
        vo.setSalePriceOverridden(config.getSalePriceOverride() != null);
        vo.setRecommendOverride(config.getRecommendOverride());
        vo.setHotOverride(config.getHotOverride());
        vo.setNewOverride(config.getNewOverride());
        vo.setSortOrderOverride(config.getSortOrderOverride());
        vo.setConfigVersion(defaultInt(config.getConfigVersion(), 1));
        if (config.getRecommendOverride() != null) vo.setIsRecommend(BooleanEnum.getByCode(config.getRecommendOverride()));
        if (config.getHotOverride() != null) vo.setIsHot(BooleanEnum.getByCode(config.getHotOverride()));
        if (config.getNewOverride() != null) vo.setIsNew(BooleanEnum.getByCode(config.getNewOverride()));
        if (config.getSortOrderOverride() != null) vo.setSortOrder(config.getSortOrderOverride());
        if (withDetails) fillDetails(vo);
        if (withPricing && effectiveListingStatus == 1 && !Integer.valueOf(1).equals(config.getSoldOut())) {
            pricingService.enrichMenuPrice(vo, config.getStoreId());
        }
        return vo;
    }

    private void fillDetails(DishVO vo) {
        vo.setSpecOptions(dishSpecOptionService.selectSpecOptionsByDishId(vo.getId()));
        DishCategory category = vo.getCategoryId() == null ? null : dishCategoryService.getById(vo.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getCategoryName());
    }

    private Map<Long, DishCategory> categoryMap(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return dishCategoryService.listByIds(ids).stream()
                .collect(Collectors.toMap(DishCategory::getId, item -> item));
    }

    private Long inferredStoreId(Long requestedStoreId) {
        if (requestedStoreId != null) {
            requireStore(requestedStoreId);
            return requestedStoreId;
        }
        if (!storeDataScopeService.requiresStoreRestriction()) return null;
        List<Long> stores = storeDataScopeService.getAllowedStoreIds();
        if (stores.size() != 1) throw new SecurityException("请选择当前有权限的门店");
        return stores.getFirst();
    }

    /** 同时校验账号门店范围和当前租户下的门店实体。 */
    private void requireStore(Long storeId) {
        storeDataScopeService.checkStoreAccess(storeId);
        if (storeInfoService.getById(storeId) == null) {
            throw new IllegalArgumentException("门店不存在或不属于当前商户");
        }
    }

    private List<Long> resolveTargetStores(List<Long> requested) {
        List<Long> ids = requested == null || requested.isEmpty()
                ? storeInfoService.list().stream().map(StoreInfo::getId).toList()
                : requested.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new BusinessException("总部菜品至少需要发布到一家门店");
        if (storeInfoService.listByIds(ids).size() != ids.size()) {
            throw new BusinessException("发布门店不存在或不属于当前商户");
        }
        return ids;
    }

    private void validatePriceOverride(Dish dish, java.math.BigDecimal price, StoreMenuPolicy policy) {
        if (price == null) return;
        if (price.signum() < 0) throw new BusinessException("门店售价不能小于0");
        java.math.BigDecimal basePrice = dish.getSalePrice();
        if (basePrice == null || basePrice.signum() <= 0) return;
        java.math.BigDecimal min = basePrice.multiply(policy.getMinPriceRate());
        java.math.BigDecimal max = basePrice.multiply(policy.getMaxPriceRate());
        if (price.compareTo(min) < 0 || price.compareTo(max) > 0) {
            throw new BusinessException("门店售价超出总部允许范围：" + min + " - " + max);
        }
    }

    private boolean sameDecimal(java.math.BigDecimal left, java.math.BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }

    private void validateCategoryScope(Long categoryId, int dishSourceType, Long ownerStoreId) {
        if (categoryId == null) return;
        DishCategory category = dishCategoryService.getById(categoryId);
        if (category == null || Integer.valueOf(1).equals(category.getDeleted())) {
            throw new IllegalArgumentException("菜品分类不存在");
        }
        int categorySource = category.getSourceType() == null
                ? (category.getStoreId() == null ? 0 : 1) : category.getSourceType();
        Long categoryOwner = category.getOwnerStoreId() == null ? category.getStoreId() : category.getOwnerStoreId();
        if (dishSourceType == 0 && categorySource != 0) {
            throw new IllegalArgumentException("总部菜品只能使用总部分类");
        }
        if (categorySource == 1 && !Objects.equals(categoryOwner, ownerStoreId)) {
            throw new IllegalArgumentException("不能使用其他门店的分类");
        }
    }

    private int normalizedSourceType(Dish dish) {
        return dish.getSourceType() == null ? (dish.getStoreId() == null ? 0 : 1) : dish.getSourceType();
    }

    private Long ownerStore(Dish dish) {
        return dish.getOwnerStoreId() == null ? dish.getStoreId() : dish.getOwnerStoreId();
    }

    private boolean enabled(Integer value) {
        return Integer.valueOf(1).equals(value);
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

/*
    @Override
    public boolean updateDishSalesCount(Long id, Integer count) {
        return dishMapper.updateDishSalesCount(id, count) > 0;
    }*/

}
