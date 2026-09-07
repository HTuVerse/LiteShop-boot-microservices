package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.enums.storeStatusEnum;
import org.hongtu.restaurant.service.StoreInfoService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 门店信息表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
public class StoreInfoServiceImpl extends ServiceImpl<StoreInfoMapper, StoreInfo> implements StoreInfoService {

    @Resource
    private StoreInfoMapper storeInfoMapper;

    @Resource
    private StoreDataScopeService storeDataScopeService;

    /**
     * 分页查询门店列表
     */
    @Override
    public Page<StoreInfo> getStorePage(Integer currentPage, Integer pageSize, String storeName, Integer status) {
        Page<StoreInfo> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<StoreInfo> wrapper = new LambdaQueryWrapper<>();

        if (storeDataScopeService.requiresStoreRestriction()) {
            List<Long> allowedStoreIds = storeDataScopeService.getAllowedStoreIds();
            if (allowedStoreIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(StoreInfo::getId, allowedStoreIds);
            }
        }

        // 条件查询
        wrapper.like(StringUtils.hasText(storeName), StoreInfo::getStoreName, storeName)
                .eq(status != null, StoreInfo::getStatus, status)
                .orderByDesc(StoreInfo::getCreatedAt);

        return this.page(page, wrapper);
    }

    /**
     * 新增门店
     */
    @Override
    public boolean addStore(StoreInfo storeInfo) {
        if (storeDataScopeService.requiresStoreRestriction()) {
            throw new SecurityException("门店管理员无权新增门店");
        }
        // 业务逻辑：校验门店编码是否已存在
        LambdaQueryWrapper<StoreInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StoreInfo::getStoreCode, storeInfo.getStoreCode());
        Long count = baseMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("门店编码已存在");
        }

        // 设置创建时间和更新时间
        storeInfo.setCreatedAt(LocalDateTime.now());
        storeInfo.setUpdatedAt(LocalDateTime.now());

        // 默认设置为营业状态
        if (storeInfo.getStatus() == null) {
            storeInfo.setStatus(storeStatusEnum.ONBUSINESS);
        }

        return this.save(storeInfo);
    }

    /**
     * 更新门店信息
     */
    @Override
    public boolean updateStore(StoreInfo storeInfo) {
        storeDataScopeService.checkStoreAccess(storeInfo.getId());
        // 业务逻辑：检查门店是否存在
        StoreInfo existingStore = this.getById(storeInfo.getId());
        if (existingStore == null) {
            throw new RuntimeException("门店不存在");
        }

        org.hongtu.security.OperationPermission.requireChange("restaurant:store:status", existingStore.getStatus(), storeInfo.getStatus());
        // 如果修改了门店编码，需要校验新编码是否已存在
        if (StringUtils.hasText(storeInfo.getStoreCode())
                && !storeInfo.getStoreCode().equals(existingStore.getStoreCode())) {
            LambdaQueryWrapper<StoreInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StoreInfo::getStoreCode, storeInfo.getStoreCode())
                    .ne(StoreInfo::getId, storeInfo.getId());
            Long count = this.baseMapper.selectCount(wrapper);
            if (count > 0) {
                throw new RuntimeException("门店编码已存在");
            }
        }

        // 设置更新时间
        storeInfo.setUpdatedAt(LocalDateTime.now());

        return this.updateById(storeInfo);
    }

    /**
     * 删除门店
     */
    @Override
    public boolean deleteStore(Long id) {
        storeDataScopeService.checkStoreAccess(id);
        // 业务逻辑：检查门店是否存在
        StoreInfo storeInfo = this.getById(id);
        if (storeInfo == null) {
            throw new RuntimeException("门店不存在");
        }

        // 业务逻辑：检查门店下是否有关联数据（如桌台信息等）
        // 这里可以根据实际业务需求添加检查逻辑
        // 例如：检查是否有未完成的订单等

        return this.removeById(id);
    }

    /**
     * 批量删除门店
     */
    @Override
    public boolean deleteStoresBatch(List<Long> ids) {
        // 业务逻辑：检查每个门店是否可以删除
        for (Long id : ids) {
            storeDataScopeService.checkStoreAccess(id);
            StoreInfo storeInfo = this.getById(id);
            if (storeInfo == null) {
                throw new RuntimeException("门店ID:" + id + " 不存在");
            }
            // 可以添加更多业务校验
        }

        return this.removeByIds(ids);
    }

    /**
     * 修改门店状态
     */
    @Override
    public boolean updateStoreStatus(Long id, Integer status) {
        storeDataScopeService.checkStoreAccess(id);
        // 业务逻辑：检查门店是否存在
        StoreInfo storeInfo = this.getById(id);
        if (storeInfo == null) {
            throw new RuntimeException("门店不存在");
        }

        // 使用LambdaUpdateWrapper更新
        LambdaUpdateWrapper<StoreInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(StoreInfo::getId, id)
                .set(StoreInfo::getStatus, status)
                .set(StoreInfo::getUpdatedAt, LocalDateTime.now());
        return this.update(updateWrapper);
    }

}
