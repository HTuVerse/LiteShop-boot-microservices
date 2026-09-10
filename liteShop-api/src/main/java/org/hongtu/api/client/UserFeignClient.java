package org.hongtu.api.client;

import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户服务 Feign 客户端
 * 对应 user-service 的 /inner/user/** 内部接口
 */
@FeignClient(name = "liteShop-user-service", contextId = "userFeignClient")
public interface UserFeignClient {

    /**
     * 查询用户（替代原单体中跨模块注入 SysUserService.getById / SysUserMapper.selectById）
     */
    @GetMapping("/inner/user/{id}")
    Result<SysUser> getUserById(@PathVariable("id") Long id);

    /**
     * 查询全部用户ID（替代原 lottery 模块 sysUserMapper.selectList(null) 全表取ID）
     */
    @GetMapping("/inner/user/ids")
    Result<List<Long>> getAllUserIds();

    /**
     * 给用户增加积分（替代原 lottery 模块直接调 SysUserService.addUserPoints）
     */
    @PostMapping("/inner/user/points/add")
    Result<Integer> addUserPoints(@RequestParam("userId") Long userId,
                                  @RequestParam("points") Integer points);

    /**
     * 余额变动（扣减/退款/充值），user-service 本地事务保证原子性
     * 替代原 restaurant 模块「改 SysUser.surplusMoney + 插 SurplusRecord」的裸操作
     *
     * @return 变动后余额
     */
    @PostMapping("/inner/user/balance/change")
    Result<BigDecimal> changeBalance(@RequestBody BalanceChangeDTO dto);

    /**
     * 查询用户角色码列表（供各服务 @SaCheckRole 使用）
     */
    @GetMapping("/inner/user/roles")
    Result<List<String>> getRoleList(@RequestParam("userId") Long userId);

    /**
     * 查询用户权限码列表（供各服务 @SaCheckPermission 使用）
     */
    @GetMapping("/inner/user/permissions")
    Result<List<String>> getPermissionList(@RequestParam("userId") Long userId);

    /** 查询用户被授权管理的门店ID。 */
    @GetMapping("/inner/user/stores")
    Result<List<Long>> getStoreIds(@RequestParam("userId") Long userId);

    /** 定时任务使用：查询所有当前有效的租户ID。 */
    @GetMapping("/inner/user/tenants/active-ids")
    Result<List<Long>> getActiveTenantIds();

    /** 请求入口使用：校验租户状态、有效期及有效套餐。 */
    @GetMapping("/inner/user/tenants/{tenantId}/active")
    Result<Boolean> isTenantActive(@PathVariable("tenantId") Long tenantId);

    /** 检查当前调用租户是否拥有功能和剩余额度。 */
    @PostMapping("/inner/saas/check")
    Result<EntitlementDecisionDTO> checkEntitlement(@RequestBody EntitlementMutationDTO dto);

    /** 原子消费配额；idempotencyKey 必须由业务操作唯一标识。 */
    @PostMapping("/inner/saas/consume")
    Result<EntitlementDecisionDTO> consumeEntitlement(@RequestBody EntitlementMutationDTO dto);

    /** 业务撤销或删除资源时，幂等释放已占用配额。 */
    @PostMapping("/inner/saas/release")
    Result<EntitlementDecisionDTO> releaseEntitlement(@RequestBody EntitlementMutationDTO dto);
}
