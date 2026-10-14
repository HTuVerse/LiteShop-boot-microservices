package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.UserBasicDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.DishMapper;
import org.hongtu.restaurant.DAO.MenuChangeRecordMapper;
import org.hongtu.restaurant.DTO.MenuChangeRecord;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.StoreMenuPolicy;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.VO.MenuChangeBatchRequest;
import org.hongtu.restaurant.VO.MenuChangeSubmitVO;
import org.hongtu.restaurant.VO.MenuChangeRecordVO;
import org.hongtu.restaurant.VO.StoreDishConfigRequest;
import org.hongtu.restaurant.enums.MenuChangeStatusEnum;
import org.hongtu.restaurant.enums.MenuChangeTypeEnum;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.beans.BeanUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单变更流程服务，统一处理审批、定时生效、批量发布和审计记录。
 */
@Service
public class MenuChangeService {

    @Resource
    private MenuChangeRecordMapper recordMapper;
    @Resource
    private DishMapper dishMapper;
    @Resource
    private UserFeignClient userFeignClient;
    @Resource
    private DishService dishService;
    @Resource
    private StoreMenuPolicyService policyService;
    @Resource
    private StoreDataScopeService storeDataScopeService;
    @Resource
    private StoreInfoService storeInfoService;
    @Resource
    private StoreGroupService storeGroupService;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private PlatformTransactionManager transactionManager;

    /** 提交单店配置变更；根据策略进入审批、待生效或立即生效。 */
    @Transactional(rollbackFor = Exception.class)
    public MenuChangeSubmitVO submit(StoreDishConfigRequest request, Long operatorId) {
        validateRequest(request);
        dishService.validateStoreDishConfig(request);
        DishVO before = dishService.getEffectiveDish(request.getDishId(), request.getStoreId(), false);
        if (request.getExpectedConfigVersion() == null) {
            request.setExpectedConfigVersion(before.getConfigVersion());
        }
        rejectConflictingChange(request.getStoreId(), request.getDishId());
        StoreMenuPolicy policy = policyService.getEffectivePolicy(request.getStoreId());
        boolean approvalRequired = storeDataScopeService.requiresStoreRestriction()
                && Integer.valueOf(1).equals(policy.getRequireApproval());
        boolean future = request.getEffectiveAt() != null
                && request.getEffectiveAt().isAfter(LocalDateTime.now());

        MenuChangeRecord record = new MenuChangeRecord()
                .setRequestNo(newRequestNo())
                .setStoreId(request.getStoreId())
                .setDishId(request.getDishId())
                .setChangeType(MenuChangeTypeEnum.STORE_CONFIG)
                .setBeforeSnapshot(toJson(snapshotFromDish(before)))
                // 生效时间和原因使用结构化列存储，不重复写入配置快照。
                .setAfterSnapshot(toJson(snapshotConfig(request)))
                .setStatus(approvalRequired ? MenuChangeStatusEnum.PENDING.getCode()
                        : future ? MenuChangeStatusEnum.SCHEDULED.getCode() : MenuChangeStatusEnum.APPROVED.getCode())
                .setChangeReason(request.getChangeReason())
                .setEffectiveAt(request.getEffectiveAt())
                .setRequestedBy(operatorId)
                .setVersion(1)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        recordMapper.insert(record);
        DishVO effective = null;
        if (!approvalRequired && !future) {
            effective = applyRecord(record);
        }
        return new MenuChangeSubmitVO().setRecord(record).setEffectiveDish(effective);
    }

    /** 总部按门店分组或指定门店列表批量发布同一配置。 */
    @Transactional(rollbackFor = Exception.class)
    public List<MenuChangeSubmitVO> submitBatch(MenuChangeBatchRequest request, Long operatorId) {
        if (storeDataScopeService.requiresStoreRestriction()) {
            throw new SecurityException("门店账号不能批量发布菜单");
        }
        if (request == null || request.getDishId() == null || request.getConfig() == null) {
            throw new IllegalArgumentException("菜品和发布配置不能为空");
        }
        List<Long> storeIds = request.getGroupId() == null ? request.getStoreIds()
                : storeGroupService.resolveEnabledGroupStores(request.getGroupId());
        if (storeIds == null || storeIds.isEmpty()) throw new IllegalArgumentException("请选择发布门店或门店分组");
        List<MenuChangeSubmitVO> results = new ArrayList<>();
        for (Long storeId : storeIds.stream().filter(Objects::nonNull).distinct().toList()) {
            DishVO current = dishService.getEffectiveDish(request.getDishId(), storeId, false);
            StoreDishConfigRequest config = new StoreDishConfigRequest();
            config.setStoreId(storeId)
                    .setDishId(request.getDishId())
                    .setCategoryOverrideId(current.getCategoryOverrideId())
                    .setSalePriceOverride(request.getConfig().getSalePriceOverride())
                    .setCostPriceOverride(current.getCostPriceOverride())
                    .setListingStatus(request.getConfig().getListingStatus())
                    .setSoldOut(request.getConfig().getSoldOut())
                    // 批量价格发布不覆盖各门店既有库存和营销标签。
                    .setInventoryMode(current.getInventoryMode())
                    .setStock(current.getStock())
                    .setRecommendOverride(current.getRecommendOverride())
                    .setHotOverride(current.getHotOverride())
                    .setNewOverride(current.getNewOverride())
                    .setSortOrderOverride(current.getSortOrderOverride())
                    .setExpectedConfigVersion(current.getConfigVersion())
                    .setEffectiveAt(request.getEffectiveAt())
                    .setChangeReason(request.getChangeReason());
            results.add(submit(config, operatorId));
        }
        return results;
    }

    /** 总部审批通过；未来时间的变更进入待生效，否则立即执行。 */
    @Transactional(rollbackFor = Exception.class)
    public MenuChangeSubmitVO approve(Long id, Long reviewerId, String remark) {
        requireHeadquarters();
        MenuChangeRecord record = requireStatus(id, MenuChangeStatusEnum.PENDING);
        record.setReviewedBy(reviewerId).setReviewRemark(remark).setReviewedAt(LocalDateTime.now());
        DishVO effective = null;
        if (record.getEffectiveAt() != null && record.getEffectiveAt().isAfter(LocalDateTime.now())) {
            changeStatus(record, MenuChangeStatusEnum.PENDING, MenuChangeStatusEnum.SCHEDULED, null);
        } else {
            changeStatus(record, MenuChangeStatusEnum.PENDING, MenuChangeStatusEnum.APPROVED, null);
            effective = applyRecord(record);
        }
        return new MenuChangeSubmitVO().setRecord(record).setEffectiveDish(effective);
    }

    /** 总部驳回待审批变更。 */
    @Transactional(rollbackFor = Exception.class)
    public MenuChangeRecord reject(Long id, Long reviewerId, String remark) {
        requireHeadquarters();
        if (!StringUtils.hasText(remark)) throw new BusinessException("驳回原因不能为空");
        MenuChangeRecord record = requireStatus(id, MenuChangeStatusEnum.PENDING);
        record.setReviewedBy(reviewerId).setReviewRemark(remark.trim()).setReviewedAt(LocalDateTime.now());
        changeStatus(record, MenuChangeStatusEnum.PENDING, MenuChangeStatusEnum.REJECTED, null);
        return record;
    }

    /** 撤销尚未生效的变更。 */
    @Transactional(rollbackFor = Exception.class)
    public MenuChangeRecord cancel(Long id, Long operatorId) {
        MenuChangeRecord record = recordMapper.selectById(id);
        if (record == null) throw new IllegalArgumentException("菜单变更记录不存在");
        storeDataScopeService.checkStoreAccess(record.getStoreId());
        if (!List.of(MenuChangeStatusEnum.PENDING.getCode(), MenuChangeStatusEnum.SCHEDULED.getCode())
                .contains(record.getStatus())) {
            throw new BusinessException("只有待审批或待生效的变更可以撤销");
        }
        if (storeDataScopeService.requiresStoreRestriction()
                && !Objects.equals(record.getRequestedBy(), operatorId)) {
            throw new SecurityException("只能撤销自己发起的菜单变更");
        }
        MenuChangeStatusEnum oldStatus = record.getStatus() == MenuChangeStatusEnum.PENDING.getCode()
                ? MenuChangeStatusEnum.PENDING : MenuChangeStatusEnum.SCHEDULED;
        changeStatus(record, oldStatus, MenuChangeStatusEnum.CANCELLED, null);
        return record;
    }

    /** 分页查询菜单变更记录。 */
    public Page<MenuChangeRecordVO> page(Integer currentPage, Integer pageSize, Long storeId,
                                         Long dishId, Integer status, String requestNo) {
        if (storeId != null) storeDataScopeService.checkStoreAccess(storeId);
        Page<MenuChangeRecord> recordPage = recordMapper.selectPage(new Page<>(currentPage, pageSize),
                new LambdaQueryWrapper<MenuChangeRecord>()
                        .eq(storeId != null, MenuChangeRecord::getStoreId, storeId)
                        .eq(dishId != null, MenuChangeRecord::getDishId, dishId)
                        .eq(status != null, MenuChangeRecord::getStatus, status)
                        .like(StringUtils.hasText(requestNo), MenuChangeRecord::getRequestNo, requestNo)
                        .orderByDesc(MenuChangeRecord::getId));
        return enrichPage(recordPage);
    }

    private Page<MenuChangeRecordVO> enrichPage(Page<MenuChangeRecord> source) {
        List<MenuChangeRecord> records = source.getRecords();
        Map<Long, Dish> dishes = records.isEmpty() ? Map.of() : dishMapper.selectBatchIds(records.stream()
                        .map(MenuChangeRecord::getDishId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
        List<Long> userIds = records.stream().map(MenuChangeRecord::getRequestedBy)
                .filter(Objects::nonNull).distinct().toList();
        List<UserBasicDTO> users = userIds.isEmpty() ? List.of()
                : FeignResultUtil.unwrap(userFeignClient.getUserBasics(userIds));
        Map<Long, UserBasicDTO> userMap = users == null ? Map.of() : users.stream()
                .collect(Collectors.toMap(UserBasicDTO::getId, Function.identity(), (left, right) -> left));

        List<MenuChangeRecordVO> values = records.stream().map(record -> {
            MenuChangeRecordVO vo = new MenuChangeRecordVO();
            BeanUtils.copyProperties(record, vo);
            Dish dish = dishes.get(record.getDishId());
            vo.setDishName(dish == null ? null : dish.getDishName());
            vo.setRequestedByName(userDisplayName(userMap.get(record.getRequestedBy())));
            vo.setRejectReason(Integer.valueOf(MenuChangeStatusEnum.REJECTED.getCode()).equals(record.getStatus())
                    ? record.getReviewRemark() : null);
            return vo;
        }).toList();
        Page<MenuChangeRecordVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(values);
        return result;
    }

    private String userDisplayName(UserBasicDTO user) {
        if (user == null) return null;
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    /** 扫描并执行已经到达生效时间的记录，供定时任务调用。 */
    public void processDueRecords() {
        List<MenuChangeRecord> records = TenantIgnore.get(() -> recordMapper.selectList(
                new LambdaQueryWrapper<MenuChangeRecord>()
                        .eq(MenuChangeRecord::getStatus, MenuChangeStatusEnum.SCHEDULED.getCode())
                        .le(MenuChangeRecord::getEffectiveAt, LocalDateTime.now())
                        .last("LIMIT 100")));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (MenuChangeRecord record : records) {
            try {
                TenantContext.setTenantId(record.getTenantId());
                transaction.executeWithoutResult(status -> {
                    // 抢占和业务生效处于同一事务：进程异常时会回滚为待生效，下次可继续处理。
                    int claimed = recordMapper.update(null, new LambdaUpdateWrapper<MenuChangeRecord>()
                            .eq(MenuChangeRecord::getId, record.getId())
                            .eq(MenuChangeRecord::getStatus, MenuChangeStatusEnum.SCHEDULED.getCode())
                            .set(MenuChangeRecord::getStatus, MenuChangeStatusEnum.APPROVED.getCode())
                            .set(MenuChangeRecord::getVersion, record.getVersion() + 1)
                            .set(MenuChangeRecord::getUpdatedAt, LocalDateTime.now()));
                    if (claimed == 0) return;
                    record.setStatus(MenuChangeStatusEnum.APPROVED.getCode())
                            .setVersion(record.getVersion() + 1);
                    applyRecord(record);
                });
            } catch (Exception error) {
                // 事务异常会把记录恢复为待生效，再明确标记失败并保留原因供人工处理。
                recordMapper.update(null, new LambdaUpdateWrapper<MenuChangeRecord>()
                        .eq(MenuChangeRecord::getId, record.getId())
                        .in(MenuChangeRecord::getStatus,
                                MenuChangeStatusEnum.SCHEDULED.getCode(), MenuChangeStatusEnum.APPROVED.getCode())
                        .set(MenuChangeRecord::getStatus, MenuChangeStatusEnum.FAILED.getCode())
                        .set(MenuChangeRecord::getFailureReason, abbreviate(error.getMessage()))
                        .setSql("version = version + 1")
                        .set(MenuChangeRecord::getUpdatedAt, LocalDateTime.now()));
            } finally {
                TenantContext.clear();
            }
        }
    }

    private DishVO applyRecord(MenuChangeRecord record) {
        if (record == null || !List.of(MenuChangeStatusEnum.APPROVED.getCode(), MenuChangeStatusEnum.SCHEDULED.getCode())
                .contains(record.getStatus())) {
            throw new BusinessException("菜单变更状态不允许执行");
        }
        StoreDishConfigRequest config = fromJson(record.getAfterSnapshot(), StoreDishConfigRequest.class);
        DishVO dish = dishService.updateStoreDishConfig(config);
        MenuChangeStatusEnum previous = record.getStatus() == MenuChangeStatusEnum.SCHEDULED.getCode()
                ? MenuChangeStatusEnum.SCHEDULED : MenuChangeStatusEnum.APPROVED;
        changeStatus(record, previous, MenuChangeStatusEnum.APPLIED, null);
        record.setAppliedAt(LocalDateTime.now());
        recordMapper.update(null, new LambdaUpdateWrapper<MenuChangeRecord>()
                .eq(MenuChangeRecord::getId, record.getId())
                .set(MenuChangeRecord::getAppliedAt, record.getAppliedAt()));
        return dish;
    }

    private void changeStatus(MenuChangeRecord record, MenuChangeStatusEnum expected,
                              MenuChangeStatusEnum target, String failureReason) {
        int updated = recordMapper.update(null, new LambdaUpdateWrapper<MenuChangeRecord>()
                .eq(MenuChangeRecord::getId, record.getId())
                .eq(MenuChangeRecord::getStatus, expected.getCode())
                .set(MenuChangeRecord::getStatus, target.getCode())
                .set(MenuChangeRecord::getReviewedBy, record.getReviewedBy())
                .set(MenuChangeRecord::getReviewRemark, record.getReviewRemark())
                .set(MenuChangeRecord::getReviewedAt, record.getReviewedAt())
                .set(MenuChangeRecord::getFailureReason, failureReason)
                .set(MenuChangeRecord::getVersion, record.getVersion() + 1)
                .set(MenuChangeRecord::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) throw new BusinessException("菜单变更已被其他操作处理，请刷新后重试");
        record.setStatus(target.getCode()).setFailureReason(failureReason)
                .setVersion(record.getVersion() + 1).setUpdatedAt(LocalDateTime.now());
    }

    private MenuChangeRecord requireStatus(Long id, MenuChangeStatusEnum status) {
        MenuChangeRecord record = recordMapper.selectById(id);
        if (record == null) throw new IllegalArgumentException("菜单变更记录不存在");
        if (!Integer.valueOf(status.getCode()).equals(record.getStatus())) {
            throw new BusinessException("菜单变更当前不是" + status.getDescription() + "状态");
        }
        return record;
    }

    private void validateRequest(StoreDishConfigRequest request) {
        if (request == null || request.getStoreId() == null || request.getDishId() == null) {
            throw new IllegalArgumentException("门店和菜品不能为空");
        }
        storeDataScopeService.checkStoreAccess(request.getStoreId());
        StoreInfo store = storeInfoService.getById(request.getStoreId());
        if (store == null) throw new IllegalArgumentException("门店不存在或不属于当前商户");
    }

    private void rejectConflictingChange(Long storeId, Long dishId) {
        Long count = recordMapper.selectCount(new LambdaQueryWrapper<MenuChangeRecord>()
                .eq(MenuChangeRecord::getStoreId, storeId)
                .eq(MenuChangeRecord::getDishId, dishId)
                .in(MenuChangeRecord::getStatus,
                        MenuChangeStatusEnum.PENDING.getCode(), MenuChangeStatusEnum.SCHEDULED.getCode()));
        if (count > 0) throw new BusinessException("该门店菜品已有待审批或待生效变更");
    }

    private void requireHeadquarters() {
        if (storeDataScopeService.requiresStoreRestriction()) {
            throw new SecurityException("门店账号不能审批菜单变更");
        }
    }

    private String newRequestNo() {
        String suffix = IdWorker.getIdStr();
        return "MC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + suffix.substring(Math.max(0, suffix.length() - 8));
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) return "未知异常";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private StoreDishConfigRequest snapshotConfig(StoreDishConfigRequest source) {
        StoreDishConfigRequest target = new StoreDishConfigRequest();
        BeanUtils.copyProperties(source, target);
        target.setEffectiveAt(null).setChangeReason(null);
        return target;
    }

    /** 只记录可变的门店菜单配置，避免把菜品详情和时间字段写入审计快照。 */
    private StoreDishConfigRequest snapshotFromDish(DishVO source) {
        return new StoreDishConfigRequest()
                .setStoreId(source.getStoreId())
                .setDishId(source.getId())
                .setCategoryOverrideId(source.getCategoryOverrideId())
                .setSalePriceOverride(source.getSalePriceOverride())
                .setCostPriceOverride(source.getCostPriceOverride())
                .setListingStatus(source.getStoreListingStatus() == null
                        ? source.getListingStatus() : source.getStoreListingStatus())
                .setSoldOut(source.getSoldOut())
                .setInventoryMode(source.getInventoryMode())
                .setStock(source.getStock())
                .setRecommendOverride(source.getRecommendOverride())
                .setHotOverride(source.getHotOverride())
                .setNewOverride(source.getNewOverride())
                .setSortOrderOverride(source.getSortOrderOverride())
                .setExpectedConfigVersion(source.getConfigVersion());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("菜单变更快照序列化失败", error);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception error) {
            throw new IllegalStateException("菜单变更快照解析失败", error);
        }
    }
}
