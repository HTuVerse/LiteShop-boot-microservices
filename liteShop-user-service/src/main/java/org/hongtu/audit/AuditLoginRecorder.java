package org.hongtu.audit;

import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.VO.SysUserVO;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** 记录管理端多阶段登录的最终结果和失败尝试，不采集口令或一次性票据。 */
@Component
@RequiredArgsConstructor
public class AuditLoginRecorder {
    private final AuditOutbox outbox;
    private final AuditEvents events;

    public <T extends Result<?>> T execute(String account, String stage, Long selectedTenant,
                                            Supplier<T> action) {
        outbox.requireReady();
        T result;
        try {
            result = action.get();
        } catch (RuntimeException error) {
            record(account, selectedTenant, "LOGIN", "管理端登录失败", "FAILURE",
                    error.getClass().getSimpleName(), null);
            throw error;
        }
        boolean success = Result.CODE_SUCCESS.equals(result.getCode());
        boolean finalLogin = success && result.getData() instanceof SysUserVO;
        SysUserVO user = finalLogin ? (SysUserVO) result.getData() : null;
        String type = finalLogin || !success ? "LOGIN" : stage;
        String summary = finalLogin ? "管理端登录成功" : success ? "管理端身份验证通过" : "管理端登录失败";
        record(account, selectedTenant, type, summary, success ? "SUCCESS" : "FAILURE",
                success ? null : "身份验证或商户选择失败", user);
        return result;
    }

    private void record(String account, Long selectedTenant, String type, String summary,
                        String result, String message, SysUserVO user) {
        AuditEvent event = events.create(account, "system:auth", type, "ADMIN_SESSION", null,
                summary, result, message, selectedTenant == null && user != null ? user.getTenantId() : selectedTenant,
                null);
        if (user != null) {
            event = new AuditEvent(event.eventId(), event.occurredAt(), user.getId(), user.getUsername(),
                    user.getTenantId(), user.getTenantId(), event.subjectTenantId(),
                    user.getActiveStoreId(), event.clientIp(), event.serviceName(), event.moduleCode(),
                    event.operationType(), event.objectType(), event.objectId(), event.summary(),
                    event.result(), event.resultMessage(), event.requestId(), event.requestPath(), null);
        }
        outbox.append(event);
    }
}
