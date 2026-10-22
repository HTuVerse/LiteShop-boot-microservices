package org.hongtu.api.client;

import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.api.dto.MallPointsChangeDTO;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.dto.AiAdvisorMessageDTO;
import org.hongtu.api.dto.AiAdvisorSessionDTO;
import org.hongtu.api.dto.AiPageDTO;
import org.hongtu.api.dto.MemberPricingDTO;
import org.hongtu.api.dto.MemberRewardSyncDTO;
import org.hongtu.api.dto.UserBasicDTO;
import org.hongtu.api.dto.TenantResourceLibraryDTO;
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

    /** 查询会员定价所需的等级信息，不传输完整用户对象。 */
    @GetMapping("/inner/user/{id}/pricing")
    Result<MemberPricingDTO> getMemberPricing(@PathVariable("id") Long id);

    /** 批量查询用户展示名称，避免业务服务分页列表逐条远程调用。 */
    @PostMapping("/inner/user/batch-basic")
    Result<List<UserBasicDTO>> getUserBasics(@RequestBody List<Long> ids);

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

    /** 带幂等键的积分增加。同一个 idempotencyKey 只入账一次。 */
    @PostMapping("/inner/user/points/add-idempotent")
    Result<Integer> addUserPointsIdempotent(@RequestParam("userId") Long userId,
                                            @RequestParam("points") Integer points,
                                            @RequestParam("idempotencyKey") String idempotencyKey);

    /** 商城兑换积分扣减及失败补偿，按订单和动作幂等。 */
    @PostMapping("/inner/user/points/mall-change")
    Result<Integer> changeMallPoints(@RequestBody MallPointsChangeDTO dto);

    /** 根据已完成餐饮订单的最新实付金额幂等同步经验和积分。 */
    @PostMapping("/inner/user/member-reward/sync")
    Result<Void> syncMemberReward(@RequestBody MemberRewardSyncDTO dto);

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

    /** 平台资源库的租户根目录名称，包含尚未上传资源的租户。 */
    @GetMapping("/inner/user/tenants/resource-library")
    Result<List<TenantResourceLibraryDTO>> getResourceLibraryTenants();

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

    /** 原子预占一次 AI 店长生成额度，同一 requestId 返回已有状态。 */
    @PostMapping("/inner/saas/ai-generation/reserve")
    Result<AiGenerationDTO> reserveAiGeneration(@RequestBody AiGenerationDTO dto);

    /** 保存成功结果及模型报告的 Token 用量。 */
    @PostMapping("/inner/saas/ai-generation/complete")
    Result<AiGenerationDTO> completeAiGeneration(@RequestBody AiGenerationDTO dto);

    /** 生成失败时按预占时的计费周期退回额度。 */
    @PostMapping("/inner/saas/ai-generation/fail")
    Result<AiGenerationDTO> failAiGeneration(@RequestBody AiGenerationDTO dto);

    /** 查询当前租户指定经营周期的 AI 店长生成记录。 */
    @GetMapping("/inner/saas/ai-generation/records")
    Result<List<AiGenerationDTO>> getAiGenerationRecords(
            @RequestParam("scopeType") String scopeType,
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam("timeRange") String timeRange);

    /** 查询当前租户指定经营周期的 AI 经营诊断仪表盘记录（含失败与进行中）。 */
    @GetMapping("/inner/saas/ai-generation/diagnosis-records")
    Result<List<AiGenerationDTO>> getAiDiagnosisRecords(
            @RequestParam("scopeType") String scopeType,
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam("timeRange") String timeRange);

    /** 创建或校验经营顾问会话。 */
    @PostMapping("/inner/saas/ai-workspace/session")
    Result<AiAdvisorSessionDTO> saveAiAdvisorSession(@RequestBody AiAdvisorSessionDTO dto);

    /** 分页查询当前用户的经营顾问会话。 */
    @GetMapping("/inner/saas/ai-workspace/sessions")
    Result<AiPageDTO<AiAdvisorSessionDTO>> getAiAdvisorSessions(
            @RequestParam("createdBy") Long createdBy,
            @RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize,
            @RequestParam(value = "status", required = false) String status);

    /** 查询会话问答历史。 */
    @GetMapping("/inner/saas/ai-workspace/sessions/{id}/messages")
    Result<List<AiAdvisorMessageDTO>> getAiAdvisorMessages(
            @PathVariable("id") Long id, @RequestParam("createdBy") Long createdBy);

    /** 保存建议有用/无用反馈。 */
    @PostMapping("/inner/saas/ai-workspace/feedback")
    Result<AiGenerationDTO> feedbackAiGeneration(@RequestBody AiGenerationDTO dto);

}
