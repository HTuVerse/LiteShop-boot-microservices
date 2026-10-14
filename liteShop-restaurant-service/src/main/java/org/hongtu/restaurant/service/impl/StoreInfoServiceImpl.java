package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DAO.StoreInfoMapper;
import org.hongtu.restaurant.enums.storeStatusEnum;
import org.hongtu.restaurant.service.StoreInfoService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    @Resource
    private UserFeignClient userFeignClient;

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
        String reservationKey = "store:create:" + storeInfo.getId();
        reserveQuota(reservationKey, BigDecimal.ONE);

        // 设置创建时间和更新时间
        storeInfo.setCreatedAt(LocalDateTime.now());
        storeInfo.setUpdatedAt(LocalDateTime.now());

        // 默认设置为营业状态
        if (storeInfo.getStatus() == null) {
            storeInfo.setStatus(storeStatusEnum.ONBUSINESS);
        }

        try {
            if (!this.save(storeInfo)) {
                throw new BusinessException(500, "新增门店失败");
            }
            return true;
        } catch (RuntimeException error) {
            compensateQuota("store:create:" + storeInfo.getId(), "store:create:rollback:" + storeInfo.getId(), error);
            throw error;
        }
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
    @Transactional(rollbackFor = Exception.class)
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

        boolean removed = this.removeById(id);
        if (removed) releaseQuota("store:create:" + id, "store:delete:" + id);
        return removed;
    }

    /**
     * 批量删除门店
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStoresBatch(List<Long> ids) {
        List<Long> normalizedIds = ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (normalizedIds.isEmpty()) throw new BusinessException(400, "门店ID列表不能为空");
        // 业务逻辑：检查每个门店是否可以删除
        for (Long id : normalizedIds) {
            storeDataScopeService.checkStoreAccess(id);
            StoreInfo storeInfo = this.getById(id);
            if (storeInfo == null) {
                throw new RuntimeException("门店ID:" + id + " 不存在");
            }
            // 可以添加更多业务校验
        }

        boolean removed = this.removeByIds(normalizedIds);
        if (removed) {
            for (Long id : normalizedIds) {
                releaseQuota("store:create:" + id, "store:delete:" + id);
            }
        }
        return removed;
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
        EntitlementDecisionDTO decision = invokeEntitlement(true, idempotencyKey, amount);
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

    private void releaseQuota(String consumeKey, String refundKey) {
        EntitlementMutationDTO request = new EntitlementMutationDTO();
        request.setFeatureCode(SaasFeatureCode.STORE_COUNT);
        request.setAmount(BigDecimal.ONE);
        request.setConsumeKey(consumeKey);
        request.setIdempotencyKey(refundKey);
        Result<EntitlementDecisionDTO> response;
        try {
            response = userFeignClient.releaseEntitlement(request);
        } catch (RuntimeException error) {
            throw new BusinessException(SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE,
                    "套餐权益服务暂不可用，为防止超额本次操作已拒绝，请稍后重试", error);
        }
        if (response == null || !Result.CODE_SUCCESS.equals(response.getCode()) || response.getData() == null
                || !response.getData().isAllowed()) {
            throw new BusinessException(SaasErrorCode.ENTITLEMENT_SERVICE_UNAVAILABLE, "门店额度释放失败");
        }
    }

    private void compensateQuota(String consumeKey, String refundKey, RuntimeException original) {
        try {
            releaseQuota(consumeKey, refundKey);
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
        }
    }

    private EntitlementDecisionDTO invokeEntitlement(boolean consume, String idempotencyKey, BigDecimal amount) {
        EntitlementMutationDTO request = new EntitlementMutationDTO();
        request.setFeatureCode(SaasFeatureCode.STORE_COUNT);
        request.setAmount(amount);
        request.setIdempotencyKey(idempotencyKey);
        final Result<EntitlementDecisionDTO> response;
        try {
            response = consume ? userFeignClient.consumeEntitlement(request) : userFeignClient.releaseEntitlement(request);
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
