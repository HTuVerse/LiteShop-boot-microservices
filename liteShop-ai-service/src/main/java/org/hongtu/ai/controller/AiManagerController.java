package org.hongtu.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.hongtu.ai.model.AiManagerRequest;
import org.hongtu.ai.model.AiManagerRecord;
import org.hongtu.ai.model.AiManagerResponse;
import org.hongtu.ai.model.AiDashboardSubmission;
import org.hongtu.ai.model.AiInsightSubmission;
import org.hongtu.ai.model.AiAdvisorReply;
import org.hongtu.ai.application.AiManagerService;
import org.hongtu.ai.application.AiWorkspaceService;
import org.hongtu.ai.client.AiAvailabilityService;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.api.dto.EntitlementDecisionDTO;
import org.hongtu.api.dto.EntitlementMutationDTO;
import org.hongtu.api.dto.AiAdvisorMessageDTO;
import org.hongtu.api.dto.AiAdvisorSessionDTO;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.dto.AiPageDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

/**
 * AI 店长第一阶段接口。
 */
@RestController
@RequestMapping("/ai/manager")
@SaCheckPermission(value = {"operations:assistant"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
public class AiManagerController {

    private final AiManagerService aiManagerService;
    private final AiAvailabilityService availability;
    private final UserFeignClient userFeignClient;
    private final AiWorkspaceService workspace;

    public AiManagerController(AiManagerService aiManagerService, AiAvailabilityService availability,
                               UserFeignClient userFeignClient, AiWorkspaceService workspace) {
        this.aiManagerService = aiManagerService;
        this.availability = availability;
        this.userFeignClient = userFeignClient;
        this.workspace = workspace;
    }

    /** 同时检查租户套餐权益和模型状态，向前端返回可展示的提示。 */
    @GetMapping("/status")
    public AiAvailabilityService.Status status() {
        if (!hasEntitlement()) {
            return new AiAvailabilityService.Status(false, "NOT_ENTITLED",
                    "当前租户暂未开通 AI 店长，可升级套餐或联系平台管理员加购。", false);
        }
        return availability.status();
    }

    /** 每次请求都校验租户的当前生效权益，不能仅依赖前端菜单。 */
    private void requireEntitlement() {
        if (!hasEntitlement()) {
            throw new BusinessException(403, "当前租户暂未开通 AI 店长，可升级套餐或联系平台管理员加购。");
        }
    }

    private boolean hasEntitlement() {
        // 平台全局视图没有租户，不能绕过租户权益读取经营数据。
        if (TenantContext.getTenantIdOrNull() == null) return false;
        EntitlementMutationDTO request = new EntitlementMutationDTO();
        request.setFeatureCode(SaasFeatureCode.AI_MANAGER);
        request.setAmount(BigDecimal.ZERO);
        EntitlementDecisionDTO decision = FeignResultUtil.unwrap(userFeignClient.checkEntitlement(request));
        return decision != null && decision.isAllowed();
    }

    /** 查询当前账号可用的租户和门店经营范围。 */
    @GetMapping("/scope")
    public Map<String, Object> scope() {
        requireEntitlement();
        return aiManagerService.scope();
    }

    /** 返回当前版本已经接入的经营数据能力，供前端过滤不可回答的预设问题。 */
    @GetMapping("/capabilities")
    public Map<String, Boolean> capabilities() {
        requireEntitlement();
        return aiManagerService.capabilities(availability.status().enabled());
    }

    /** 生成当前范围的经营简报。 */
    @PostMapping("/briefing")
    @SaCheckPermission(value = {"operations:ai:diagnosis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiManagerResponse briefing(@Valid @RequestBody AiManagerRequest request) {
        requireEntitlement();
        return aiManagerService.briefing(request);
    }

    /** 提交 AI 经营诊断后台生成任务，立即返回请求ID；离开页面后仍会继续生成并落库快照。 */
    @PostMapping("/diagnosis-dashboard")
    @SaCheckPermission(value = {"operations:ai:diagnosis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiDashboardSubmission diagnosisDashboard(@Valid @RequestBody AiManagerRequest request) {
        requireEntitlement();
        return aiManagerService.submitDashboard(request);
    }

    /** 查询当前范围和周期的经营诊断记录，用于自动恢复最新结果与历史回看。 */
    @GetMapping("/diagnosis-records")
    @SaCheckPermission(value = {"operations:ai:diagnosis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public List<AiManagerRecord> diagnosisRecords(@RequestParam String scopeType,
                                                  @RequestParam(required = false) Long storeId,
                                                  @RequestParam String timeRange) {
        requireEntitlement();
        return aiManagerService.diagnosisRecords(scopeType, storeId, timeRange);
    }

    /** 生成近30天菜品经营建议。 */
    @PostMapping("/dish-advice")
    @SaCheckPermission(value = {"operations:ai:diagnosis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiManagerResponse dishAdvice(@Valid @RequestBody AiManagerRequest request) {
        requireEntitlement();
        return aiManagerService.dishAdvice(request);
    }

    /** 后台生成经营简报和菜品建议，客户端离开页面不会终止任务。 */
    @PostMapping("/insights")
    @SaCheckPermission(value = {"operations:ai:diagnosis"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiInsightSubmission insights(@Valid @RequestBody AiManagerRequest request) {
        requireEntitlement();
        requireInsightQuota();
        return aiManagerService.submitInsights(request);
    }

    /** 查询当前范围和实际周期的分析状态及历史版本。 */
    @GetMapping("/records")
    @SaCheckPermission(value = {"operations:ai:records"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public List<AiManagerRecord> records(@RequestParam String scopeType,
                                         @RequestParam(required = false) Long storeId,
                                         @RequestParam String timeRange) {
        requireEntitlement();
        return aiManagerService.records(scopeType, storeId, timeRange);
    }

    private void requireInsightQuota() {
        EntitlementMutationDTO quotaRequest = new EntitlementMutationDTO();
        quotaRequest.setFeatureCode(SaasFeatureCode.AI_MANAGER_CALLS);
        quotaRequest.setAmount(BigDecimal.ONE);
        EntitlementDecisionDTO decision = FeignResultUtil.unwrap(
                userFeignClient.checkEntitlement(quotaRequest));
        if (decision == null || !decision.isAllowed()) {
            throw new BusinessException(SaasErrorCode.QUOTA_EXCEEDED,
                    "AI 店长需要剩余 1 次生成额度才能发起经营诊断");
        }
    }

    /** 生成营销活动草稿，第一阶段不会直接创建活动。 */
    @PostMapping("/marketing-draft")
    @SaCheckPermission(value = {"operations:ai:plan:create"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiManagerResponse marketingDraft(@Valid @RequestBody AiManagerRequest request) {
        requireEntitlement();
        return aiManagerService.marketingDraft(request);
    }

    /** 回答基于当前经营范围的数据问题。 */
    @PostMapping("/chat")
    @SaCheckPermission(value = {"operations:ai:advisor"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiAdvisorReply chat(@Valid @RequestBody AiManagerRequest request) {
        requireEntitlement();
        return aiManagerService.chat(request);
    }

    /** 新建经营顾问会话；首次提问也会自动创建。 */
    @PostMapping("/advisor/sessions")
    @SaCheckPermission(value = {"operations:ai:advisor"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiAdvisorSessionDTO createSession(@RequestBody AiAdvisorSessionDTO request) {
        requireEntitlement();
        return workspace.saveSession(request, StpUtil.getLoginIdAsLong());
    }

    /** 服务端分页查询当前用户的会话。 */
    @GetMapping("/advisor/sessions")
    @SaCheckPermission(value = {"operations:ai:advisor"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiPageDTO<AiAdvisorSessionDTO> sessions(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        requireEntitlement();
        return workspace.sessions(StpUtil.getLoginIdAsLong(), currentPage, pageSize, status);
    }

    /** 重命名或归档当前用户自己的会话。 */
    @PostMapping("/advisor/sessions/{id}")
    @SaCheckPermission(value = {"operations:ai:advisor"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiAdvisorSessionDTO updateSession(@PathVariable Long id, @RequestBody AiAdvisorSessionDTO request) {
        requireEntitlement();
        request.setId(id);
        return workspace.saveSession(request, StpUtil.getLoginIdAsLong());
    }

    /** 打开已有会话不扣减额度。 */
    @GetMapping("/advisor/sessions/{id}/messages")
    @SaCheckPermission(value = {"operations:ai:advisor"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public List<AiAdvisorMessageDTO> messages(@PathVariable Long id) {
        requireEntitlement();
        return workspace.messages(id, StpUtil.getLoginIdAsLong());
    }

    /** 标记建议有用、无用或忽略。 */
    @PostMapping("/records/{requestId}/feedback")
    @SaCheckPermission(value = {"operations:ai:records"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public AiGenerationDTO feedback(@PathVariable String requestId, @RequestBody Map<String, String> body) {
        requireEntitlement();
        return workspace.feedback(requestId, body.get("feedback"), StpUtil.getLoginIdAsLong());
    }
}
