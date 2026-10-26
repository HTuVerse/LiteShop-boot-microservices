package org.hongtu.ai.application;

import org.hongtu.ai.model.AiManagerRequest;
import org.hongtu.ai.model.AiManagerResponse;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** 通过用户服务统一预占、结算并记录 AI 店长的周期调用次数。 */
@Service
public class AiManagerQuotaService {
    private final UserFeignClient users;
    private final ObjectMapper json;

    public AiManagerQuotaService(UserFeignClient users, ObjectMapper json) {
        this.users = users;
        this.json = json;
    }

    /** 同一 requestId 与请求内容只允许生成一次。 */
    public AiGenerationDTO reserve(String operation, AiManagerRequest request) {
        return reserve(operation, request, true);
    }

    /** 完整诊断只由主分析扣一次额度，子分析仍保留独立幂等记录。 */
    public AiGenerationDTO reserve(String operation, AiManagerRequest request, boolean chargeQuota) {
        if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BusinessException(400, "缺少 AI 请求ID");
        }
        // CHAT 历史由服务端实时恢复，重试时可能增长；幂等键只包含本轮稳定参数。
        Object hashPayload = "CHAT".equals(operation) ? java.util.Arrays.asList(
                request.getRequestId(), request.getCreatedBy(), request.getScopeType(),
                request.getStoreId() == null ? "" : request.getStoreId(), request.getTimeRange(),
                request.getSessionId(), request.getMessage(),
                request.getReferenceRequestId() == null ? "" : request.getReferenceRequestId()) : request;
        AiGenerationDTO dto = new AiGenerationDTO().setRequestId(request.getRequestId())
                .setOperation(operation).setRequestHash(hash(operation + ":" + json.writeValueAsString(hashPayload)))
                .setCreatedBy(request.getCreatedBy()).setScopeType(upper(request.getScopeType(), "TENANT"))
                .setStoreId("STORE".equals(upper(request.getScopeType(), "TENANT")) ? request.getStoreId() : null)
                .setTimeRange(upper(request.getTimeRange(), "TODAY"))
                .setPeriodKey(periodKey(upper(request.getTimeRange(), "TODAY")))
                .setSessionId(request.getSessionId()).setInputText(request.getMessage())
                .setReferenceRequestId(request.getReferenceRequestId())
                .setQuotaCharged(chargeQuota);
        return FeignResultUtil.unwrap(users.reserveAiGeneration(dto));
    }

    /** 查询当前范围和实际周期的分析记录。 */
    public List<AiGenerationDTO> records(String scopeType, Long storeId, String timeRange) {
        String normalizedScope = upper(scopeType, "TENANT");
        String normalizedRange = upper(timeRange, "TODAY");
        return FeignResultUtil.unwrap(users.getAiGenerationRecords(normalizedScope,
                "STORE".equals(normalizedScope) ? storeId : null,
                normalizedRange));
    }

    /** 查询当前范围和实际周期的经营诊断仪表盘记录（含失败与进行中）。 */
    public List<AiGenerationDTO> diagnosisRecords(String scopeType, Long storeId, String timeRange) {
        String normalizedScope = upper(scopeType, "TENANT");
        String normalizedRange = upper(timeRange, "TODAY");
        return FeignResultUtil.unwrap(users.getAiDiagnosisRecords(normalizedScope,
                "STORE".equals(normalizedScope) ? storeId : null,
                normalizedRange));
    }

    public AiManagerResponse cached(AiGenerationDTO reservation) {
        return switch (reservation.getStatus()) {
            case "SUCCEEDED" -> json.readValue(reservation.getResponseJson(), AiManagerResponse.class);
            case "PROCESSING" -> throw new BusinessException(409, "该 AI 请求仍在生成，请稍后使用同一请求ID重试");
            case "FAILED" -> throw new BusinessException(409, "上次 AI 请求已失败，请重新发起");
            default -> throw new BusinessException(500, "AI 请求状态不正确");
        };
    }

    public void complete(String requestId, AiManagerResponse result, ChatResponse modelResponse) {
        var usage = modelResponse == null || modelResponse.getMetadata() == null
                ? null : modelResponse.getMetadata().getUsage();
        AiGenerationDTO dto = new AiGenerationDTO().setRequestId(requestId)
                .setResponseJson(json.writeValueAsString(result))
                .setPromptTokens(usage == null || usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue())
                .setCompletionTokens(usage == null || usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().longValue());
        FeignResultUtil.unwrap(users.completeAiGeneration(dto));
    }

    public void fail(String requestId) {
        fail(requestId, "TASK_INTERRUPTED", "生成任务未完成，可以重新发起", true);
    }

    /** 记录经过脱敏的失败原因，便于前端区分可重试与配置故障。 */
    public void fail(String requestId, String failureCode, String failureMessage, boolean retryable) {
        FeignResultUtil.unwrap(users.failAiGeneration(new AiGenerationDTO().setRequestId(requestId)
                .setFailureCode(failureCode).setFailureMessage(failureMessage).setRetryable(retryable)));
    }

    private String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    public String periodKey(String timeRange) {
        LocalDate today = LocalDate.now();
        return switch (timeRange) {
            case "YESTERDAY" -> today.minusDays(1).toString();
            case "WEEK" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case "MONTH" -> YearMonth.from(today).toString();
            default -> today.toString();
        };
    }

    private String upper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
