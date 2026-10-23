package org.hongtu.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.SysUserStoreMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.SysUserStore;
import org.hongtu.Result.Result;
import org.hongtu.api.client.RestaurantFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.service.SysUserStoreService;
import org.hongtu.tenant.TenantConstants;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SysUserStoreServiceImpl extends ServiceImpl<SysUserStoreMapper, SysUserStore>
        implements SysUserStoreService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private RestaurantFeignClient restaurantFeignClient;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<org.hongtu.VO.UserStoreAuthorizationVO> authorizationPage(
            int currentPage, int pageSize, String keyword, Long storeId, Boolean authorized) {
        Long tenantId = TenantContext.getTenantId();
        var page = baseMapper.authorizationPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(1, currentPage), Math.min(100, Math.max(1, pageSize))), tenantId, keyword, storeId, authorized);
        if (!page.getRecords().isEmpty()) {
            var ids = page.getRecords().stream().map(org.hongtu.VO.UserStoreAuthorizationVO::getUserId).toList();
            var grouped = lambdaQuery().in(SysUserStore::getUserId, ids).orderByDesc(SysUserStore::getIsDefault).list().stream()
                    .collect(java.util.stream.Collectors.groupingBy(SysUserStore::getUserId));
            for (var row : page.getRecords()) {
                var links = grouped.getOrDefault(row.getUserId(), List.of());
                row.setStoreIds(links.stream().map(SysUserStore::getStoreId).toList());
                row.setDefaultStoreId(links.stream().filter(link -> Integer.valueOf(1).equals(link.getIsDefault()))
                        .map(SysUserStore::getStoreId).findFirst().orElse(null));
            }
        }
        return page;
    }

    @Override
    public List<Long> getStoreIds(Long userId) {
        return lambdaQuery().eq(SysUserStore::getUserId, userId)
                .orderByDesc(SysUserStore::getIsDefault)
                .list()
                .stream()
                .map(SysUserStore::getStoreId)
                .toList();
    }

    @Override
    public Result<Void> assignStores(Long userId, List<Long> storeIds, Long defaultStoreId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在或不在当前租户");
        }
        if (user.getTenantId() == null) {
            return Result.error("平台超级管理员不分配门店");
        }
        boolean platformTenantScope = TenantContext.isSuperAdmin()
                && TenantContext.getTenantIdOrNull() == null;
        if (platformTenantScope) return TenantScope.call(user.getTenantId(),
                () -> assignStoresInCurrentTenant(user, storeIds, defaultStoreId));
        return assignStoresInCurrentTenant(user, storeIds, defaultStoreId);
    }

    private Result<Void> assignStoresInCurrentTenant(SysUser user, List<Long> storeIds, Long defaultStoreId) {
        List<Long> normalized = storeIds == null ? List.of()
                : new LinkedHashSet<>(storeIds).stream().toList();
        if (defaultStoreId != null && !normalized.contains(defaultStoreId)) {
            return Result.error("默认门店必须包含在授权门店列表中");
        }
        if (!normalized.isEmpty()
                && !Boolean.TRUE.equals(FeignResultUtil.unwrap(restaurantFeignClient.validateStoreIds(normalized)))) {
            return Result.error("授权门店不存在或不属于当前租户");
        }

        // 跨服务校验在事务外完成，避免持有用户行锁等待餐厅服务。
        return transactionTemplate.execute(status -> persistStoreAssignment(user, normalized, defaultStoreId));
    }

    private Result<Void> persistStoreAssignment(SysUser user, List<Long> normalized, Long defaultStoreId) {
        SysUser locked = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, user.getId()).last("FOR UPDATE"));
        if (locked == null || !user.getTenantId().equals(locked.getTenantId())) {
            return Result.error("用户已不存在或租户已变更");
        }
        remove(new LambdaQueryWrapper<SysUserStore>().eq(SysUserStore::getUserId, user.getId()));
        if (!normalized.isEmpty()) {
            List<SysUserStore> rows = normalized.stream().map(storeId -> new SysUserStore()
                    .setTenantId(user.getTenantId())
                    .setUserId(user.getId())
                    .setStoreId(storeId)
                    .setIsDefault(storeId.equals(defaultStoreId) ? 1 : 0)
                    .setCreateTime(LocalDateTime.now())).toList();
            if (!saveBatch(rows)) throw new IllegalStateException("保存门店授权失败");
        }
        SaSession loginSession = StpUtil.getSessionByLoginId(user.getId(), false);
        if (loginSession != null) {
            org.hongtu.service.SessionAfterCommit.run(() -> {
                loginSession.set(TenantConstants.SESSION_AUTHORIZED_STORE_IDS, normalized);
                boolean restricted = Boolean.parseBoolean(String.valueOf(
                        loginSession.get(TenantConstants.SESSION_STORE_RESTRICTED)));
                if (!restricted) {
                    loginSession.set(TenantConstants.SESSION_STORE_IDS, List.of());
                    loginSession.delete(TenantConstants.SESSION_ACTIVE_STORE_ID);
                    return;
                }
                Object currentValue = loginSession.get(TenantConstants.SESSION_ACTIVE_STORE_ID);
                Long current = currentValue == null ? null : Long.valueOf(currentValue.toString());
                Long selected = current != null && normalized.contains(current)
                        ? current
                        : defaultStoreId != null ? defaultStoreId
                        : normalized.size() == 1 ? normalized.getFirst() : null;
                if (selected == null) {
                    loginSession.delete(TenantConstants.SESSION_ACTIVE_STORE_ID);
                    loginSession.set(TenantConstants.SESSION_STORE_IDS, List.of());
                } else {
                    loginSession.set(TenantConstants.SESSION_ACTIVE_STORE_ID, selected);
                    loginSession.set(TenantConstants.SESSION_STORE_IDS, List.of(selected));
                }
            });
        }
        return Result.success("门店授权成功", null);
    }
}
