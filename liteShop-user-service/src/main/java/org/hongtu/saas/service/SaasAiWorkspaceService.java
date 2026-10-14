package org.hongtu.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.api.dto.AiAdvisorMessageDTO;
import org.hongtu.api.dto.AiAdvisorSessionDTO;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.dto.AiPageDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.saas.domain.SaasAiAdvisorSession;
import org.hongtu.saas.domain.SaasAiGeneration;
import org.hongtu.saas.mapper.SaasAiAdvisorSessionMapper;
import org.hongtu.saas.mapper.SaasAiGenerationMapper;
import org.hongtu.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 经营顾问会话与建议反馈服务。 */
@Service
public class SaasAiWorkspaceService {
    @Resource private SaasAiAdvisorSessionMapper sessions;
    @Resource private SaasAiGenerationMapper generations;

    /** 新建会话，或校验已有会话属于当前租户和当前用户。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAdvisorSessionDTO saveSession(AiAdvisorSessionDTO request) {
        if (request == null || request.getCreatedBy() == null) throw new BusinessException(400, "缺少会话用户");
        Long tenantId = TenantContext.getTenantId();
        if (request.getId() != null) {
            SaasAiAdvisorSession old = requiredSession(request.getId(), request.getCreatedBy());
            boolean changed = false;
            if (StringUtils.hasText(request.getTitle())) {
                old.setTitle(safe(request.getTitle(), 100));
                changed = true;
            }
            if (StringUtils.hasText(request.getStatus())) {
                String status = upper(request.getStatus());
                if (!Set.of("ACTIVE", "ARCHIVED").contains(status)) throw new BusinessException(400, "会话状态不正确");
                old.setStatus(status);
                changed = true;
            }
            if (changed) {
                old.setUpdatedAt(LocalDateTime.now());
                sessions.updateById(old);
            }
            return sessionView(old);
        }
        validateScope(request.getScopeType(), request.getStoreId());
        if (StringUtils.hasText(request.getInitialRequestId())) {
            SaasAiAdvisorSession existing = sessions.selectOne(new LambdaQueryWrapper<SaasAiAdvisorSession>()
                    .eq(SaasAiAdvisorSession::getTenantId, tenantId)
                    .eq(SaasAiAdvisorSession::getCreatedBy, request.getCreatedBy())
                    .eq(SaasAiAdvisorSession::getInitialRequestId, request.getInitialRequestId()));
            if (existing != null) return sessionView(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        SaasAiAdvisorSession row = new SaasAiAdvisorSession()
                .setTenantId(tenantId).setTitle(safe(defaultText(request.getTitle(), "新会话"), 100))
                .setScopeType(upper(request.getScopeType())).setStoreId(request.getStoreId())
                .setStatus("ACTIVE").setDiagnosisRequestId(safe(request.getDiagnosisRequestId(), 64))
                .setInitialRequestId(safe(request.getInitialRequestId(), 64))
                .setCreatedBy(request.getCreatedBy()).setLastMessageAt(now).setCreatedAt(now).setUpdatedAt(now);
        try {
            sessions.insert(row);
        } catch (DuplicateKeyException duplicate) {
            if (StringUtils.hasText(request.getInitialRequestId())) {
                SaasAiAdvisorSession existing = sessions.selectOne(new LambdaQueryWrapper<SaasAiAdvisorSession>()
                        .eq(SaasAiAdvisorSession::getTenantId, tenantId)
                        .eq(SaasAiAdvisorSession::getCreatedBy, request.getCreatedBy())
                        .eq(SaasAiAdvisorSession::getInitialRequestId, request.getInitialRequestId()));
                if (existing != null) return sessionView(existing);
            }
            throw duplicate;
        }
        return sessionView(row);
    }

    /** 当前用户的会话使用服务端分页。 */
    public AiPageDTO<AiAdvisorSessionDTO> sessions(Long createdBy, Integer currentPage, Integer pageSize, String status) {
        int current = Math.max(1, currentPage == null ? 1 : currentPage);
        int size = Math.min(50, Math.max(1, pageSize == null ? 20 : pageSize));
        String normalizedStatus = StringUtils.hasText(status) ? upper(status) : "ACTIVE";
        Page<SaasAiAdvisorSession> page = sessions.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<SaasAiAdvisorSession>()
                        .eq(SaasAiAdvisorSession::getTenantId, TenantContext.getTenantId())
                        .eq(SaasAiAdvisorSession::getCreatedBy, createdBy)
                        .eq(SaasAiAdvisorSession::getStatus, normalizedStatus)
                        .orderByDesc(SaasAiAdvisorSession::getLastMessageAt));
        return new AiPageDTO<AiAdvisorSessionDTO>().setRecords(page.getRecords().stream().map(this::sessionView).toList())
                .setTotal(page.getTotal()).setCurrent(page.getCurrent()).setSize(page.getSize());
    }

    /** 按生成记录恢复一轮一轮的问答，不再次调用模型或扣减额度。 */
    public List<AiAdvisorMessageDTO> messages(Long sessionId, Long createdBy) {
        requiredSession(sessionId, createdBy);
        return generations.selectList(new LambdaQueryWrapper<SaasAiGeneration>()
                        .eq(SaasAiGeneration::getTenantId, TenantContext.getTenantId())
                        .eq(SaasAiGeneration::getCreatedBy, createdBy)
                        .eq(SaasAiGeneration::getOperation, "CHAT")
                        .eq(SaasAiGeneration::getSessionId, sessionId)
                        .orderByAsc(SaasAiGeneration::getCreatedAt))
                .stream().map(row -> new AiAdvisorMessageDTO().setRequestId(row.getRequestId())
                        .setSessionId(row.getSessionId()).setQuestion(row.getInputText())
                        .setResponseJson(row.getResponseJson()).setStatus(row.getStatus())
                        .setFailureMessage(row.getFailureMessage()).setFeedback(row.getFeedback())
                        .setCreatedAt(row.getCreatedAt()).setUpdatedAt(row.getUpdatedAt())).toList();
    }

    /** 对生成结果标记有用或无用，重复反馈覆盖但不重复计费。 */
    @Transactional(rollbackFor = Exception.class)
    public AiGenerationDTO feedback(AiGenerationDTO request) {
        if (request == null || !StringUtils.hasText(request.getRequestId()) || request.getCreatedBy() == null) {
            throw new BusinessException(400, "缺少建议反馈参数");
        }
        SaasAiGeneration row = generations.selectOne(new LambdaQueryWrapper<SaasAiGeneration>()
                .eq(SaasAiGeneration::getTenantId, TenantContext.getTenantId())
                .eq(SaasAiGeneration::getRequestId, request.getRequestId()));
        if (row == null) throw new BusinessException(404, "AI 生成记录不存在");
        if (!Objects.equals(row.getCreatedBy(), request.getCreatedBy())) throw new BusinessException(403, "不能评价他人的建议");
        row.setFeedback(normalizedFeedback(request.getFeedback())).setFeedbackAt(LocalDateTime.now());
        generations.updateById(row);
        return new AiGenerationDTO().setRequestId(row.getRequestId()).setFeedback(row.getFeedback())
                .setFeedbackAt(row.getFeedbackAt());
    }

    /** 生成成功后更新会话标题和最近活跃时间。 */
    public void touchSession(SaasAiGeneration generation) {
        if (generation.getSessionId() == null) return;
        SaasAiAdvisorSession row = sessions.selectById(generation.getSessionId());
        if (row == null || !Objects.equals(row.getTenantId(), generation.getTenantId())
                || !Objects.equals(row.getCreatedBy(), generation.getCreatedBy())) return;
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<SaasAiAdvisorSession> update = new LambdaUpdateWrapper<SaasAiAdvisorSession>()
                .eq(SaasAiAdvisorSession::getId, row.getId()).set(SaasAiAdvisorSession::getLastMessageAt, now)
                .set(SaasAiAdvisorSession::getUpdatedAt, now);
        if ("新会话".equals(row.getTitle()) && StringUtils.hasText(generation.getInputText())) {
            update.set(SaasAiAdvisorSession::getTitle, safe(generation.getInputText(), 30));
        }
        sessions.update(null, update);
    }

    private void validateScope(String scopeType, Long storeId) {
        String scope = upper(scopeType);
        if (!Set.of("TENANT", "STORE").contains(scope)
                || ("STORE".equals(scope) && storeId == null)
                || ("TENANT".equals(scope) && storeId != null)) {
            throw new BusinessException(400, "经营范围不正确");
        }
    }

    private SaasAiAdvisorSession requiredSession(Long id, Long createdBy) {
        SaasAiAdvisorSession row = sessions.selectOne(new LambdaQueryWrapper<SaasAiAdvisorSession>()
                .eq(SaasAiAdvisorSession::getTenantId, TenantContext.getTenantId())
                .eq(SaasAiAdvisorSession::getId, id).eq(SaasAiAdvisorSession::getCreatedBy, createdBy));
        if (row == null) throw new BusinessException(404, "经营顾问会话不存在");
        return row;
    }

    private AiAdvisorSessionDTO sessionView(SaasAiAdvisorSession row) {
        return new AiAdvisorSessionDTO().setId(row.getId()).setTitle(row.getTitle())
                .setScopeType(row.getScopeType()).setStoreId(row.getStoreId()).setStatus(row.getStatus())
                .setDiagnosisRequestId(row.getDiagnosisRequestId()).setCreatedBy(row.getCreatedBy())
                .setInitialRequestId(row.getInitialRequestId())
                .setLastMessageAt(row.getLastMessageAt()).setCreatedAt(row.getCreatedAt()).setUpdatedAt(row.getUpdatedAt());
    }

    private String normalizedFeedback(String feedback) {
        String value = upper(feedback);
        if (!Set.of("USEFUL", "NOT_USEFUL", "IGNORED").contains(value)) throw new BusinessException(400, "反馈类型不正确");
        return value;
    }

    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String defaultText(String value, String fallback) { return StringUtils.hasText(value) ? value : fallback; }
    private String safe(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
