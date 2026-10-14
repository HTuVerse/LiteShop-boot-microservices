package org.hongtu.ai.application;

import org.hongtu.ai.model.AiManagerRequest;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.AiAdvisorMessageDTO;
import org.hongtu.api.dto.AiAdvisorSessionDTO;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.dto.AiPageDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/** AI 店长会话与建议反馈的跨服务编排。 */
@Service
public class AiWorkspaceService {
    private final UserFeignClient users;
    private final AiManagerScopeGuard scopeGuard;

    public AiWorkspaceService(UserFeignClient users, AiManagerScopeGuard scopeGuard) {
        this.users = users;
        this.scopeGuard = scopeGuard;
    }

    public AiAdvisorSessionDTO openSession(AiManagerRequest request) {
        AiAdvisorSessionDTO session = new AiAdvisorSessionDTO().setId(request.getSessionId())
                .setScopeType(request.getScopeType()).setStoreId(request.getStoreId())
                .setDiagnosisRequestId(request.getReferenceRequestId()).setInitialRequestId(request.getRequestId())
                .setTitle(request.getSessionId() == null ? request.getMessage() : null)
                .setCreatedBy(request.getCreatedBy());
        return FeignResultUtil.unwrap(users.saveAiAdvisorSession(session));
    }

    public AiAdvisorSessionDTO saveSession(AiAdvisorSessionDTO session, Long userId) {
        session.setCreatedBy(userId);
        if (session.getId() == null) scopeGuard.validate(session.getScopeType(), session.getStoreId());
        return FeignResultUtil.unwrap(users.saveAiAdvisorSession(session));
    }

    public AiPageDTO<AiAdvisorSessionDTO> sessions(Long userId, Integer currentPage, Integer pageSize, String status) {
        return FeignResultUtil.unwrap(users.getAiAdvisorSessions(userId, currentPage, pageSize, status));
    }

    public List<AiAdvisorMessageDTO> messages(Long sessionId, Long userId) {
        return FeignResultUtil.unwrap(users.getAiAdvisorMessages(sessionId, userId));
    }

    public AiGenerationDTO feedback(String requestId, String feedback, Long userId) {
        return FeignResultUtil.unwrap(users.feedbackAiGeneration(new AiGenerationDTO()
                .setRequestId(requestId).setFeedback(feedback).setCreatedBy(userId)));
    }
}
