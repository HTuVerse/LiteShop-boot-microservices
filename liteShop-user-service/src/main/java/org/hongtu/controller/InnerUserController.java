package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.SysTenant;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.service.SysUserService;
import org.hongtu.service.SysTenantService;
import org.hongtu.service.SysUserStoreService;
import org.hongtu.service.impl.InnerBalanceService;
import org.hongtu.saas.service.SaasSubscriptionService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户服务内部接口（仅供其他微服务通过 Feign 调用）
 * 外部经网关访问 /inner/** 会被全局过滤器拦截
 */
@Slf4j
@RestController
@RequestMapping("/inner/user")
@SaIgnore
public class InnerUserController {

    @Resource
    private SysUserService sysUserService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysPermissionMapper sysPermissionMapper;

    @Resource
    private InnerBalanceService innerBalanceService;

    @Resource
    private SysUserStoreService userStoreService;

    @Resource
    private SysTenantService tenantService;

    @Resource
    private SaasSubscriptionService subscriptions;

    /**
     * 查询用户（替代原跨模块 SysUserService.getById / SysUserMapper.selectById）
     */
    @GetMapping("/{id}")
    public Result<SysUser> getUserById(@PathVariable("id") Long id) {
        return Result.success(sysUserService.getById(id));
    }

    /**
     * 查询全部用户ID（替代原 lottery 模块全表查询取ID）
     */
    @GetMapping("/ids")
    public Result<List<Long>> getAllUserIds() {
        List<Long> ids = sysUserMapper.selectList(null)
                .stream()
                .map(SysUser::getId)
                .toList();
        return Result.success(ids);
    }

    /**
     * 增加用户积分（替代原 lottery 模块直接调 SysUserService.addUserPoints）
     */
    @PostMapping("/points/add")
    public Result<Integer> addUserPoints(@RequestParam("userId") Long userId,
                                         @RequestParam("points") Integer points) {
        Result result = sysUserService.addUserPoints(userId, points);
        if (!Result.CODE_SUCCESS.equals(result.getCode())) {
            return Result.error(result.getMsg());
        }
        return Result.success(points);
    }

    /**
     * 余额变动（user-service 本地事务保证原子性）
     */
    @PostMapping("/balance/change")
    public Result<BigDecimal> changeBalance(@RequestBody BalanceChangeDTO dto) {
        return Result.success(innerBalanceService.changeBalance(dto));
    }

    /**
     * 查询用户角色码列表（供其他服务的 @SaCheckRole 使用）
     */
    @GetMapping("/roles")
    public Result<List<String>> getRoleList(@RequestParam("userId") Long userId) {
        List<String> roles = TenantContext.isSuperAdmin()
                ? TenantIgnore.get(() -> sysRoleMapper.selectRoleCodesByUserId(userId))
                : sysRoleMapper.selectRoleCodesByUserId(userId);
        return Result.success(roles == null ? new ArrayList<>() : roles);
    }

    /**
     * 查询用户权限码列表（供其他服务的 @SaCheckPermission 使用）
     */
    @GetMapping("/permissions")
    public Result<List<String>> getPermissionList(@RequestParam("userId") Long userId) {
        List<String> permissions = TenantContext.isSuperAdmin()
                ? TenantIgnore.get(() -> sysPermissionMapper.selectPermissionCodesByUserId(userId))
                : sysPermissionMapper.selectPermissionCodesByUserId(userId);
        return Result.success(permissions == null ? new ArrayList<>() : permissions);
    }

    @GetMapping("/stores")
    public Result<List<Long>> getStoreIds(@RequestParam("userId") Long userId) {
        return Result.success(userStoreService.getStoreIds(userId));
    }

    @GetMapping("/tenants/active-ids")
    public Result<List<Long>> getActiveTenantIds() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getStatus, 1)
                .and(condition -> condition.isNull(SysTenant::getExpireTime)
                        .or().gt(SysTenant::getExpireTime, now))
                .select(SysTenant::getId)
                .orderByAsc(SysTenant::getId);
        List<Long> tenantIds = tenantService.list(wrapper).stream().map(SysTenant::getId).toList();
        return Result.success(tenantIds);
    }

    @GetMapping("/tenants/{tenantId}/active")
    public Result<Boolean> isTenantActive(@PathVariable Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return Result.success(false);
        }
        SysTenant tenant = tenantService.getById(tenantId);
        boolean active = tenant != null
                && Integer.valueOf(1).equals(tenant.getStatus())
                && (tenant.getExpireTime() == null
                || tenant.getExpireTime().isAfter(LocalDateTime.now()))
                && TenantScope.call(tenantId, () -> subscriptions.hasEffectiveSubscription(tenantId));
        return Result.success(active);
    }
}
