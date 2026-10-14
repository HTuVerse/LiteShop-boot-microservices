package org.hongtu.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.hongtu.api.constant.SaasErrorCode;
import org.hongtu.api.constant.SaasFeatureCode;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.saas.domain.SaasAiGeneration;
import org.hongtu.saas.mapper.SaasAiGenerationMapper;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** AI 店长请求与 SaaS 调用配额的一致性协调。 */
@Service
public class SaasAiGenerationService {
    private static final Set<String> OPERATIONS = Set.of("BRIEFING", "DISH_ADVICE", "MARKETING_DRAFT", "CHAT",
            "DIAGNOSIS_DASHBOARD");
    private static final Set<String> SCOPE_TYPES = Set.of("TENANT", "STORE");
    private static final Set<String> TIME_RANGES = Set.of("TODAY", "YESTERDAY", "WEEK", "MONTH");
    @Resource private SaasAiGenerationMapper generations;
    @Resource private SaasEntitlementService entitlements;
    @Resource private SaasAiWorkspaceService workspace;

    /** 同一租户请求只预占一次；已成功的请求直接返回先前结果。 */
    @Transactional(rollbackFor = Exception.class)
    public AiGenerationDTO reserve(AiGenerationDTO request) {
        Long tenantId = TenantContext.getTenantId();
        validateRequest(request);
        SaasAiGeneration old = find(tenantId, request.getRequestId(), false);
        if (old != null) {
            if (!Objects.equals(old.getRequestHash(), request.getRequestHash())
                    || !Objects.equals(old.getOperation(), request.getOperation())) {
                throw new BusinessException(409, "同一请求ID不能用于不同的 AI 操作");
            }
            return view(old);
        }
        if (!entitlements.hasFeature(tenantId, SaasFeatureCode.AI_MANAGER)) {
            throw new BusinessException(SaasErrorCode.FEATURE_DISABLED, "当前租户未开通 AI 店长");
        }
        LocalDateTime now = LocalDateTime.now();
        SaasAiGeneration row = new SaasAiGeneration().setTenantId(tenantId)
                .setRequestId(request.getRequestId()).setRequestHash(request.getRequestHash())
                .setOperation(request.getOperation()).setCreatedBy(request.getCreatedBy())
                .setScopeType(request.getScopeType()).setStoreId(request.getStoreId())
                .setTimeRange(request.getTimeRange()).setPeriodKey(request.getPeriodKey())
                .setQuotaCharged(request.getQuotaCharged() == null || request.getQuotaCharged())
                .setSessionId(request.getSessionId()).setInputText(safeText(request.getInputText(), 1000))
                .setReferenceRequestId(safeText(request.getReferenceRequestId(), 64))
                .setStatus("PROCESSING")
                .setCreatedAt(now).setUpdatedAt(now);
        // 唯一键阻止并发重试各自扣减；冲突请求由客户端稍后查询同一 requestId。
        try { generations.insert(row); }
        catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "该 AI 请求正在生成，请用相同请求ID稍后重试");
        }
        if (Boolean.TRUE.equals(row.getQuotaCharged())) {
            var decision = entitlements.consume(tenantId, SaasFeatureCode.AI_MANAGER_CALLS,
                    BigDecimal.ONE, consumeKey(request.getRequestId()));
            if (!decision.isAllowed()) {
                throw new BusinessException(SaasErrorCode.QUOTA_EXCEEDED, "AI 店长本周期调用次数已用完，可加购次数包");
            }
        }
        return view(row).setAcquired(true);
    }

    /** 成功结果与 Token 统计落库后，重试不再重复调用模型。 */
    @Transactional(rollbackFor = Exception.class)
    public AiGenerationDTO complete(AiGenerationDTO request) {
        SaasAiGeneration row = required(request, true);
        if ("SUCCEEDED".equals(row.getStatus())) return view(row);
        if (!"PROCESSING".equals(row.getStatus())) throw new BusinessException(409, "AI 请求已失败，请重新发起");
        if (request.getResponseJson() == null || request.getResponseJson().isBlank()
                || request.getResponseJson().length() > 20000) {
            throw new BusinessException(400, "AI 生成结果为空或过长");
        }
        row.setResponseJson(request.getResponseJson())
                .setPromptTokens(nonNegative(request.getPromptTokens()))
                .setCompletionTokens(nonNegative(request.getCompletionTokens()))
                .setStatus("SUCCEEDED").setFailureCode(null).setFailureMessage(null).setRetryable(null)
                .setUpdatedAt(LocalDateTime.now());
        generations.updateById(row);
        if ("CHAT".equals(row.getOperation())) workspace.touchSession(row);
        return view(row);
    }

    /** 失败退款仅执行一次，且使用原消费事件对应的周期。 */
    @Transactional(rollbackFor = Exception.class)
    public AiGenerationDTO fail(AiGenerationDTO request) {
        SaasAiGeneration row = required(request, true);
        if (!"PROCESSING".equals(row.getStatus())) return view(row);
        if (row.getQuotaCharged() == null || Boolean.TRUE.equals(row.getQuotaCharged())) {
            entitlements.refundConsumption(row.getTenantId(), consumeKey(row.getRequestId()), refundKey(row.getRequestId()));
        }
        row.setStatus("FAILED")
                .setFailureCode(safeText(request.getFailureCode(), 64))
                .setFailureMessage(safeText(request.getFailureMessage(), 500))
                .setRetryable(request.getRetryable() == null || request.getRetryable())
                .setUpdatedAt(LocalDateTime.now());
        generations.updateById(row);
        return view(row);
    }

    /** 定时恢复进程中断后遗留的预占。 */
    public List<SaasAiGeneration> stale(LocalDateTime before) {
        return generations.selectStale(before);
    }

    /** 查询一个实际经营周期内的简报与菜品分析历史。 */
    public List<AiGenerationDTO> records(String scopeType, Long storeId, String timeRange) {
        String normalizedScope = scopeType == null ? "" : scopeType.trim().toUpperCase();
        String normalizedRange = timeRange == null ? "" : timeRange.trim().toUpperCase();
        if (!SCOPE_TYPES.contains(normalizedScope) || !TIME_RANGES.contains(normalizedRange)) {
            throw new BusinessException(400, "AI 分析记录查询条件不正确");
        }
        if ("STORE".equals(normalizedScope) && (storeId == null || storeId <= 0)) {
            throw new BusinessException(400, "请选择要查询的门店");
        }
        LambdaQueryWrapper<SaasAiGeneration> query = new LambdaQueryWrapper<SaasAiGeneration>()
                .eq(SaasAiGeneration::getTenantId, TenantContext.getTenantId())
                .in(SaasAiGeneration::getOperation, "BRIEFING", "DISH_ADVICE")
                .eq(SaasAiGeneration::getScopeType, normalizedScope)
                .eq(SaasAiGeneration::getTimeRange, normalizedRange)
                .orderByDesc(SaasAiGeneration::getCreatedAt)
                .last("LIMIT 40");
        if ("STORE".equals(normalizedScope)) query.eq(SaasAiGeneration::getStoreId, storeId);
        else query.isNull(SaasAiGeneration::getStoreId);
        return generations.selectList(query).stream().map(this::view).toList();
    }

    /** 查询一个经营周期内的诊断仪表盘记录，含成功、失败和进行中，用于自动恢复与历史回看。 */
    public List<AiGenerationDTO> diagnosisRecords(String scopeType, Long storeId, String timeRange) {
        String normalizedScope = scopeType == null ? "" : scopeType.trim().toUpperCase();
        String normalizedRange = timeRange == null ? "" : timeRange.trim().toUpperCase();
        if (!SCOPE_TYPES.contains(normalizedScope) || !TIME_RANGES.contains(normalizedRange)) {
            throw new BusinessException(400, "AI 诊断记录查询条件不正确");
        }
        if ("STORE".equals(normalizedScope) && (storeId == null || storeId <= 0)) {
            throw new BusinessException(400, "请选择要查询的门店");
        }
        LambdaQueryWrapper<SaasAiGeneration> query = new LambdaQueryWrapper<SaasAiGeneration>()
                .eq(SaasAiGeneration::getTenantId, TenantContext.getTenantId())
                .eq(SaasAiGeneration::getOperation, "DIAGNOSIS_DASHBOARD")
                .eq(SaasAiGeneration::getScopeType, normalizedScope)
                .eq(SaasAiGeneration::getTimeRange, normalizedRange)
                .orderByDesc(SaasAiGeneration::getCreatedAt)
                .last("LIMIT 20");
        if ("STORE".equals(normalizedScope)) query.eq(SaasAiGeneration::getStoreId, storeId);
        else query.isNull(SaasAiGeneration::getStoreId);
        return generations.selectList(query).stream().map(this::view).toList();
    }

    private SaasAiGeneration required(AiGenerationDTO request, boolean lock) {
        if (request == null || request.getRequestId() == null) throw new BusinessException(400, "缺少 AI 请求ID");
        SaasAiGeneration row = find(TenantContext.getTenantId(), request.getRequestId(), lock);
        if (row == null) throw new BusinessException(404, "AI 请求不存在");
        return row;
    }

    private SaasAiGeneration find(Long tenantId, String requestId, boolean lock) {
        LambdaQueryWrapper<SaasAiGeneration> query = new LambdaQueryWrapper<SaasAiGeneration>()
                .eq(SaasAiGeneration::getTenantId, tenantId)
                .eq(SaasAiGeneration::getRequestId, requestId);
        if (lock) query.last("FOR UPDATE");
        return generations.selectOne(query);
    }

    private void validateRequest(AiGenerationDTO request) {
        if (request == null || request.getRequestId() == null || request.getRequestId().length() > 64
                || request.getRequestHash() == null || !request.getRequestHash().matches("[0-9a-f]{64}")
                || !OPERATIONS.contains(request.getOperation())
                || !SCOPE_TYPES.contains(request.getScopeType())
                || !TIME_RANGES.contains(request.getTimeRange())
                || request.getPeriodKey() == null || request.getPeriodKey().isBlank()
                || ("STORE".equals(request.getScopeType()) && request.getStoreId() == null)) {
            throw new BusinessException(400, "AI 请求ID、摘要或操作类型不正确");
        }
        try { UUID.fromString(request.getRequestId()); }
        catch (IllegalArgumentException error) { throw new BusinessException(400, "AI 请求ID必须是 UUID"); }
    }

    private Long nonNegative(Long count) {
        if (count != null && count < 0) throw new BusinessException(400, "Token 用量不能为负数");
        return count;
    }

    private AiGenerationDTO view(SaasAiGeneration row) {
        return new AiGenerationDTO().setRequestId(row.getRequestId()).setRequestHash(row.getRequestHash())
                .setOperation(row.getOperation()).setStatus(row.getStatus())
                .setFailureCode(row.getFailureCode()).setFailureMessage(row.getFailureMessage())
                .setRetryable(row.getRetryable())
                .setQuotaCharged(row.getQuotaCharged())
                .setSessionId(row.getSessionId()).setInputText(row.getInputText())
                .setReferenceRequestId(row.getReferenceRequestId()).setFeedback(row.getFeedback())
                .setFeedbackAt(row.getFeedbackAt())
                .setCreatedBy(row.getCreatedBy()).setScopeType(row.getScopeType()).setStoreId(row.getStoreId())
                .setTimeRange(row.getTimeRange()).setPeriodKey(row.getPeriodKey())
                .setResponseJson(row.getResponseJson()).setPromptTokens(row.getPromptTokens())
                .setCompletionTokens(row.getCompletionTokens()).setCreatedAt(row.getCreatedAt())
                .setUpdatedAt(row.getUpdatedAt());
    }

    private String consumeKey(String requestId) { return "ai-call:" + requestId; }
    private String refundKey(String requestId) { return "ai-refund:" + requestId; }

    private String safeText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
