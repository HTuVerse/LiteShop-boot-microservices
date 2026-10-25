package org.hongtu.restaurant.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.api.dto.StoreDirectoryDTO;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.enums.storeStatusEnum;
import org.hongtu.restaurant.service.StoreInfoService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.service.LocalOutboxService;
import org.hongtu.tenant.TenantContext;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Resource
    private UserFeignClient userFeignClient;
    @Resource
    private ResourceBindingCoordinator resourceBindings;
    @Resource
    private LocalOutboxService localOutboxService;

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
    @Transactional(rollbackFor = Exception.class)
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

        // 服务端生成业务 ID，并用该 ID 作为本次占额的稳定标识。
        storeInfo.setId(IdWorker.getId());
        ResourceBindingPlan imagePlan = resourceBindings.prepareMulti("STORE", storeInfo.getId(), "storeImages",
                storeInfo.getStoreImageResourceIds(), storeInfo.getStoreImages());
        storeInfo.setStoreImages(resourceBindings.resolvedMulti(imagePlan, storeInfo.getStoreImages()));
        String reservationKey = "store:create:" + storeInfo.getId();
        // 门店数量由所属服务单向推送，权益服务不再同步反查餐厅服务。
        ensureStoreCountBaseline();
        // 占额先写独立发件箱；门店事务回滚时由对账任务按消费键释放额度。
        localOutboxService.executeRemote("STORE_QUOTA_RESERVE", reservationKey,
                JSONUtil.createObj().set("storeId", storeInfo.getId()).toString(),
                () -> reserveQuota(reservationKey, BigDecimal.ONE));

        // 设置创建时间和更新时间
        storeInfo.setCreatedAt(LocalDateTime.now());
        storeInfo.setUpdatedAt(LocalDateTime.now());

        // 默认设置为营业状态
        if (storeInfo.getStatus() == null) {
            storeInfo.setStatus(storeStatusEnum.ONBUSINESS);
        }

        if (!this.save(storeInfo)) throw new BusinessException(500, "新增门店失败");
        localOutboxService.enqueueStoreDirectoryChange(directoryChange(storeInfo, true));
        resourceBindings.completeAfterCommit(imagePlan);
        return true;
    }

    /**
     * 更新门店信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStore(StoreInfo storeInfo) {
        if (storeInfo == null || storeInfo.getId() == null) throw new BusinessException(400, "门店ID不能为空");
        storeDataScopeService.checkStoreAccess(storeInfo.getId());
        // 业务逻辑：检查门店是否存在
        List<StoreInfo> locked = storeInfoMapper.lockStores(TenantContext.getTenantId(), List.of(storeInfo.getId()));
        StoreInfo existingStore = locked.isEmpty() ? null : locked.getFirst();
        if (existingStore == null) {
            throw new RuntimeException("门店不存在");
        }
        if (storeInfo.getStoreImages() == null) storeInfo.setStoreImages(existingStore.getStoreImages());
        if (storeInfo.getStoreImageResourceIds() == null)
            storeInfo.setStoreImageResourceIds(java.util.Objects.equals(storeInfo.getStoreImages(), existingStore.getStoreImages())
                    ? resourceBindings.currentMultiIds("STORE", storeInfo.getId(), "storeImages", existingStore.getStoreImages()) : "");
        ResourceBindingPlan imagePlan = resourceBindings.prepareMulti("STORE", storeInfo.getId(), "storeImages",
                storeInfo.getStoreImageResourceIds(), storeInfo.getStoreImages());
        storeInfo.setStoreImages(resourceBindings.resolvedMulti(imagePlan, storeInfo.getStoreImages()));

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

        boolean updated = this.updateById(storeInfo);
        if (updated) {
            StoreInfo current = this.getById(storeInfo.getId());
            localOutboxService.enqueueStoreDirectoryChange(directoryChange(current, true));
            resourceBindings.completeAfterCommit(imagePlan);
        }
        return updated;
    }

    /**
     * 删除门店
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStore(Long id) {
        if (id == null) throw new BusinessException(400, "门店ID不能为空");
        return deleteStoresBatch(List.of(id));
    }

    /**
     * 批量删除门店
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStoresBatch(List<Long> ids) {
        List<Long> normalizedIds = ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (normalizedIds.isEmpty()) throw new BusinessException(400, "门店ID列表不能为空");
        if (normalizedIds.size() > 100) throw new BusinessException(400, "单次最多删除100家门店");
        for (Long id : normalizedIds) {
            storeDataScopeService.checkStoreAccess(id);
        }
        List<StoreInfo> stores = storeInfoMapper.lockStores(TenantContext.getTenantId(), normalizedIds);
        if (stores.size() != normalizedIds.size()) throw new BusinessException("部分门店不存在或不属于当前租户");
        Long referenced = storeInfoMapper.findReferencedStoreId(TenantContext.getTenantId(), normalizedIds);
        if (referenced != null)
            throw new BusinessException("门店ID:" + referenced + " 存在关联业务数据，请设置为停业，不能直接删除");
        // 存量门店可能只有基线、没有逐店消费事件；删除前确保释放有对应计数。
        ensureStoreCountBaseline();

        if (storeInfoMapper.deleteStores(TenantContext.getTenantId(), normalizedIds) != normalizedIds.size())
            throw new BusinessException("门店状态已变化，请刷新后重试");
        Map<Long, StoreInfo> deletedStores = stores.stream()
                .collect(Collectors.toMap(StoreInfo::getId, store -> store));
        for (Long id : normalizedIds) {
            resourceBindings.clearAfterCommit("STORE", id, "storeImages");
            // 同一事务记下释放事件，提交后投递；失败由发件箱重试。
            localOutboxService.enqueueStoreQuotaRelease(id);
            localOutboxService.enqueueStoreDirectoryChange(directoryChange(deletedStores.get(id), false));
        }
        return true;
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

    private void reserveQuota(String idempotencyKey, BigDecimal amount) {
        EntitlementDecisionDTO decision = invokeEntitlement(idempotencyKey, amount);
        if (!decision.isAllowed()) {
            String reason = decision.getReason() == null ? "" : decision.getReason();
            String message = switch (reason) {
                case "FEATURE_DISABLED" -> "当前套餐未开通门店额度，请先分配已发布的套餐版本";
                case "QUOTA_EXCEEDED" -> "门店数量已达到当前套餐上限，请升级套餐或购买额外门店";
                default -> StringUtils.hasText(reason) ? reason
                        : "门店数量已达到当前套餐上限，请升级套餐或购买额外门店";
            };
            int errorCode = "FEATURE_DISABLED".equals(reason)
                    ? SaasErrorCode.FEATURE_DISABLED : SaasErrorCode.QUOTA_EXCEEDED;
            throw new BusinessException(errorCode, message);
        }
    }

    private void ensureStoreCountBaseline() {
        Result<Void> baseline = userFeignClient.ensureStoreCountBaseline(
                baseMapper.selectCount(new LambdaQueryWrapper<StoreInfo>()));
        if (baseline == null || !Result.CODE_SUCCESS.equals(baseline.getCode()))
            throw new BusinessException(SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE,
                    "门店用量初始化失败，请稍后重试");
    }

    private StoreDirectoryDTO directoryChange(StoreInfo store, boolean active) {
        StoreDirectoryDTO change = new StoreDirectoryDTO();
        change.setStoreId(store.getId());
        change.setStoreName(store.getStoreName());
        change.setActive(active);
        change.setSyncVersion(IdWorker.getId());
        return change;
    }

    private EntitlementDecisionDTO invokeEntitlement(String idempotencyKey, BigDecimal amount) {
        EntitlementMutationDTO request = new EntitlementMutationDTO();
        request.setFeatureCode(SaasFeatureCode.STORE_COUNT);
        request.setAmount(amount);
        request.setIdempotencyKey(idempotencyKey);
        final Result<EntitlementDecisionDTO> response;
        try {
            response = userFeignClient.consumeEntitlement(request);
        } catch (RuntimeException error) {
            throw new BusinessException(SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE,
                    "套餐权益服务暂不可用，为防止超额本次操作已拒绝，请稍后重试", error);
        }
        if (response == null || !Result.CODE_SUCCESS.equals(response.getCode()) || response.getData() == null) {
            String message = response == null || !StringUtils.hasText(response.getMsg())
                    ? "套餐权益服务返回异常，为防止超额本次操作已拒绝" : response.getMsg();
            int code = response != null && response.getCode() != null ? response.getCode() : SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE;
            throw new BusinessException(code, message);
        }
        return response.getData();
    }

}
