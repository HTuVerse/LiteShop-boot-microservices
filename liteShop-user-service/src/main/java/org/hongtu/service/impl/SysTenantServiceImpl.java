package org.hongtu.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.DAO.SysTenantMapper;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.DAO.SysRolePermissionMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DTO.SysPermission;
import org.hongtu.DTO.SysRolePermission;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.SysRole;
import org.hongtu.DTO.SysTenant;
import org.hongtu.Result.Result;
import org.hongtu.service.SysTenantService;
import org.hongtu.saas.domain.SaasTenantSubscription;
import org.hongtu.saas.domain.TenantCreateRequest;
import org.hongtu.saas.service.SaasSubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hongtu.enums.StatusEnum;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

    @Resource
    private org.hongtu.service.UserLevelSchemeService levelSchemes;
    @Resource private org.springframework.context.ApplicationEventPublisher events;
    @Resource private SaasSubscriptionService subscriptions;

    @Resource
    private SysRoleMapper roleMapper;

    @Resource
    private SysUserMapper userMapper;

    @Resource
    private SysPermissionMapper permissionMapper;

    @Resource
    private SysRolePermissionMapper rolePermissionMapper;

    @Override
    public SysTenant findByCode(String tenantCode) {
        if (StrUtil.isBlank(tenantCode)) {
            return null;
        }
        return lambdaQuery()
                .eq(SysTenant::getTenantCode, normalizeCode(tenantCode))
                .one();
    }

    @Override
    public Result<Page<SysTenant>> pageTenants(Integer currentPage, Integer pageSize,
                                                String keyword, Integer status) {
        Page<SysTenant> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<SysTenant>()
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(SysTenant::getTenantCode, keyword)
                        .or()
                        .like(SysTenant::getTenantName, keyword))
                .eq(status != null, SysTenant::getStatus, status)
                .orderByDesc(SysTenant::getCreateTime);
        return Result.success(page(page, wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<SysTenant> addTenant(TenantCreateRequest request) {
        subscriptions.requirePublishedVersion(request.getPlanVersionId());
        SysTenant tenant = new SysTenant()
                .setTenantCode(request.getTenantCode())
                .setTenantName(request.getTenantName())
                .setContactName(request.getContactName())
                .setContactPhone(request.getContactPhone())
                .setStatus(request.getStatus())
                .setRemark(request.getRemark());
        tenant.setTenantCode(normalizeCode(tenant.getTenantCode()));
        if (lambdaQuery().eq(SysTenant::getTenantCode, tenant.getTenantCode()).count() > 0) {
            return Result.error("租户编码已存在");
        }
        if (lambdaQuery().eq(SysTenant::getTenantName, tenant.getTenantName()).count() > 0) {
            return Result.error("租户名称已存在");
        }
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        if (!save(tenant)) {
            return Result.error("新增租户失败");
        }
        TenantScope.run(tenant.getId(), () -> { initializeRoles(tenant.getId()); levelSchemes.initializeTenant(); });
        subscriptions.assign(tenant.getId(), new SaasTenantSubscription()
                .setPlanVersionId(request.getPlanVersionId())
                .setBillingCycle(request.getBillingCycle())
                .setStatus(request.getSubscriptionStatus())
                .setAutoRenew(request.getAutoRenew())
                .setCurrentPeriodStart(request.getCurrentPeriodStart())
                .setCurrentPeriodEnd(request.getCurrentPeriodEnd())
                .setTrialEndAt(request.getTrialEndAt()));
        events.publishEvent(new org.hongtu.service.TenantCreatedEvent(tenant.getId(), tenant.getTenantName()));
        return Result.success("新增租户成功", getById(tenant.getId()));
    }

    @Override
    public Result<SysTenant> updateTenant(SysTenant tenant) {
        SysTenant existing = getById(tenant.getId());
        if (existing == null) {
            return Result.error("租户不存在");
        }
        tenant.setTenantCode(normalizeCode(tenant.getTenantCode()));
        if (lambdaQuery().eq(SysTenant::getTenantCode, tenant.getTenantCode())
                .ne(SysTenant::getId, tenant.getId()).count() > 0) {
            return Result.error("租户编码已存在");
        }
        // 资料编辑只更新资料字段；状态由专用接口处理，服务到期时间由套餐订阅维护。
        boolean updated = lambdaUpdate().eq(SysTenant::getId, tenant.getId())
                .set(SysTenant::getTenantCode, tenant.getTenantCode())
                .set(SysTenant::getTenantName, tenant.getTenantName())
                .set(SysTenant::getContactName, tenant.getContactName())
                .set(SysTenant::getContactPhone, tenant.getContactPhone())
                .set(SysTenant::getRemark, tenant.getRemark())
                .set(SysTenant::getUpdateTime, LocalDateTime.now())
                .update();
        return updated ? Result.success("更新租户成功", getById(tenant.getId())) : Result.error("更新租户失败");
    }

    @Override
    public Result<Void> updateStatus(Long tenantId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            return Result.error("租户状态只能是0或1");
        }
        if (status == 1 && !subscriptions.hasEffectiveSubscription(tenantId)) {
            return Result.error("商户没有有效套餐，不能启用");
        }
        boolean updated = lambdaUpdate().eq(SysTenant::getId, tenantId)
                .set(SysTenant::getStatus, status)
                .set(SysTenant::getUpdateTime, LocalDateTime.now())
                .update();
        if (updated && status == 0) {
            List<Long> userIds = TenantIgnore.get(() -> userMapper.selectList(
                            new LambdaQueryWrapper<SysUser>().eq(SysUser::getTenantId, tenantId))
                    .stream().map(SysUser::getId).toList());
            userIds.forEach(StpUtil::logout);
        }
        return updated ? Result.success("租户状态更新成功", null) : Result.error("租户不存在");
    }

    private String normalizeCode(String code) {
        return StrUtil.trim(code).toUpperCase(Locale.ROOT);
    }

    private void initializeRoles(Long tenantId) {
        List<SysPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getStatus, StatusEnum.NORMAL));
        assignDefaultPermissions(createRole(tenantId, "商户管理员", "ADMIN", "管理本租户全部门店与人员"), permissions);
        assignDefaultPermissions(createRole(tenantId, "门店管理员", "STORE_ADMIN", "管理被授权门店"), permissions);
        assignDefaultPermissions(createRole(tenantId, "门店员工", "STORE_STAFF", "操作被授权门店业务"), permissions);
        createRole(tenantId, "普通用户", "USER", "租户消费者");
    }

    private SysRole createRole(Long tenantId, String name, String code, String description) {
        SysRole role = new SysRole()
                .setTenantId(tenantId)
                .setRoleName(name)
                .setRoleCode(code)
                .setDescription(description)
                .setStatus(StatusEnum.NORMAL)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());
        roleMapper.insert(role);
        return role;
    }

    private void assignDefaultPermissions(SysRole role, List<SysPermission> permissions) {
        permissions.stream()
                .filter(permission -> shouldAssign(role.getRoleCode(), permission.getPermissionCode()))
                .forEach(permission -> rolePermissionMapper.insert(new SysRolePermission()
                        .setTenantId(role.getTenantId())
                        .setRoleId(role.getId())
                        .setPermissionId(permission.getId())));
    }

    private boolean shouldAssign(String roleCode, String permissionCode) {
        if (permissionCode == null) {
            return false;
        }
        // 审计导出需要运营单独授权，新建租户也不能随 ADMIN 默认权限获得。
        if ("system:audit:export".equals(permissionCode)) return false;
        if (("common:console".equals(permissionCode) || "common:profile".equals(permissionCode))
                && java.util.Set.of("ADMIN", "STORE_ADMIN", "STORE_STAFF").contains(roleCode)) return true;
        return switch (roleCode) {
            case "ADMIN" -> !permissionCode.startsWith("platform:");
            case "STORE_ADMIN" -> permissionCode.startsWith("restaurant:");
            case "STORE_STAFF" -> permissionCode.equals("restaurant:menu")
                    || permissionCode.startsWith("restaurant:order")
                    || permissionCode.startsWith("restaurant:table");
            default -> false;
        };
    }
}
