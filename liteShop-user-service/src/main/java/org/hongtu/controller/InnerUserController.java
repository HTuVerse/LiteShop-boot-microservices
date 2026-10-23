package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.SurplusRecordMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.SysTenant;
import org.hongtu.DTO.UserSurplusRecord;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.api.dto.MallPointsChangeDTO;
import org.hongtu.api.dto.MemberPricingDTO;
import org.hongtu.api.dto.MemberRewardSyncDTO;
import org.hongtu.api.dto.UserBasicDTO;
import org.hongtu.service.SysUserService;
import org.hongtu.service.MemberOrderRewardService;
import org.hongtu.service.SysTenantService;
import org.hongtu.service.SysUserStoreService;
import org.hongtu.service.impl.InnerBalanceService;
import org.hongtu.service.impl.MallPointsAccountService;
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
    private SurplusRecordMapper surplusRecordMapper;

    @Resource
    private MallPointsAccountService mallPointsAccountService;

    @Resource
    private MemberOrderRewardService memberOrderRewardService;

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

    /** 仅返回会员价计算所需字段，避免无关用户字段造成跨服务反序列化失败。 */
    @GetMapping("/{id}/pricing")
    public Result<MemberPricingDTO> getMemberPricing(@PathVariable("id") Long id) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getLevel)
                .eq(SysUser::getId, id)
                .last("LIMIT 1"));
        if (user == null) {
            return Result.error("会员不存在");
        }
        return Result.success(new MemberPricingDTO().setId(user.getId()).setLevel(user.getLevel()));
    }

    /** 仅返回业务列表展示所需字段，不向其他服务暴露密码、余额等用户信息。 */
    @PostMapping("/batch-basic")
    public Result<List<UserBasicDTO>> getUserBasics(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Result.success(List.of());
        List<Long> distinctIds = ids.stream().filter(java.util.Objects::nonNull).distinct().limit(200).toList();
        if (distinctIds.isEmpty()) return Result.success(List.of());
        List<UserBasicDTO> users = sysUserMapper.selectList(
                        new LambdaQueryWrapper<SysUser>()
                                .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname)
                                .in(SysUser::getId, distinctIds))
                .stream()
                .map(user -> new UserBasicDTO().setId(user.getId())
                        .setUsername(user.getUsername()).setNickname(user.getNickname()))
                .toList();
        return Result.success(users);
    }

    /**
     * 查询全部用户ID（替代原 lottery 模块全表查询取ID）
     */
    @GetMapping("/ids")
    public Result<List<Long>> getAllUserIds() {
        // 旧接口保留兼容，但拒绝无界全表读取；新调用方使用游标接口。
        return Result.error(410, "请使用 /inner/user/ids/page 游标接口");
    }

    @GetMapping("/ids/page")
    public Result<List<Long>> getUserIdPage(@RequestParam(defaultValue = "0") Long afterId,
                                             @RequestParam(defaultValue = "500") Integer limit) {
        if (afterId == null || afterId < 0 || limit == null || limit < 1 || limit > 500) {
            return Result.error(400, "分页参数无效");
        }
        return Result.success(sysUserMapper.selectIdPage(TenantContext.getTenantId(), afterId, limit));
    }

    /**
     * 增加用户积分（替代原 lottery 模块直接调 SysUserService.addUserPoints）
     */
    @PostMapping("/points/add-idempotent")
    public Result<Integer> addUserPointsIdempotent(@RequestParam("userId") Long userId,
                                                   @RequestParam("points") Integer points,
                                                   @RequestParam("idempotencyKey") String idempotencyKey) {
        Result result = sysUserService.addUserPointsIdempotent(userId, points, idempotencyKey);
        if (!Result.CODE_SUCCESS.equals(result.getCode())) {
            return Result.error(result.getMsg());
        }
        return Result.success(points);
    }

    @PostMapping("/points/add")
    public Result<Integer> addUserPoints(@RequestParam("userId") Long userId,
                                         @RequestParam("points") Integer points) {
        Result result = sysUserService.addUserPoints(userId, points);
        if (!Result.CODE_SUCCESS.equals(result.getCode())) {
            return Result.error(result.getMsg());
        }
        return Result.success(points);
    }

    /** 兑换订单积分变动，按订单和动作幂等。 */
    @PostMapping("/points/mall-change")
    public Result<Integer> changeMallPoints(@RequestBody MallPointsChangeDTO dto) {
        return Result.success(mallPointsAccountService.change(dto));
    }

    /** 同步餐饮订单完成奖励或退款后的撤回金额。 */
    @PostMapping("/member-reward/sync")
    public Result<Void> syncMemberReward(@RequestBody MemberRewardSyncDTO dto) {
        memberOrderRewardService.sync(dto);
        return Result.success();
    }

    /**
     * 余额变动（user-service 本地事务保证原子性）
     */
    @PostMapping("/balance/change")
    public Result<BigDecimal> changeBalance(@RequestBody BalanceChangeDTO dto) {
        return Result.success(innerBalanceService.changeBalance(dto));
    }

    /** 查询本租户已提交的余额流水，不向调用方返回资金明细。 */
    @GetMapping("/balance/applied")
    public Result<Boolean> balanceChangeApplied(@RequestParam String orderNo,
                                                @RequestParam Long userId,
                                                @RequestParam Integer type) {
        UserSurplusRecord record = surplusRecordMapper.selectOne(new LambdaQueryWrapper<UserSurplusRecord>()
                .eq(UserSurplusRecord::getOrderNo, orderNo).last("LIMIT 1"));
        if (record == null) return Result.success(false);
        if (!userId.equals(record.getUserId()) || record.getType() == null
                || !type.equals(record.getType().getCode())) {
            throw new IllegalStateException("余额幂等键已被其他操作占用");
        }
        return Result.success(true);
    }

    /** 撤销一次已提交的退款入账，重复请求使用同一撤销幂等键。 */
    @PostMapping("/balance/reverse-credit")
    public Result<Boolean> reverseBalanceCredit(@RequestParam String orderNo,
                                                @RequestParam Long userId,
                                                @RequestParam String reversalNo) {
        return Result.success(innerBalanceService.reverseCredit(orderNo, userId, reversalNo));
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

    @GetMapping("/tenants/resource-library")
    public Result<List<org.hongtu.api.dto.TenantResourceLibraryDTO>> resourceLibraryTenants() {
        return Result.success(TenantIgnore.get(() -> tenantService.list().stream()
                .map(tenant -> new org.hongtu.api.dto.TenantResourceLibraryDTO(
                        tenant.getId(), tenant.getTenantName())).toList()));
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
