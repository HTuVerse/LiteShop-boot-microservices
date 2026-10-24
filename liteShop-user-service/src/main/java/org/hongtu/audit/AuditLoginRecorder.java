package org.hongtu.audit;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.VO.SysUserVO;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.utils.RedisCache;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.function.Supplier;

/** 记录管理端多阶段登录的最终结果和失败尝试，不采集口令或一次性票据。 */
@Component
@RequiredArgsConstructor
public class AuditLoginRecorder {
    private final AuditOutbox outbox;
    private final AuditEvents events;
    private final RedisCache redis;
    private final SysUserMapper users;

    public <T extends Result<?>> T execute(String account, String stage, Long selectedTenant,
                                            Supplier<T> action) {
        outbox.requireReady();
        String resolvedAccount = account == null ? accountFromTicket(stage, selectedTenant) : account;
        // 无效商户选择凭证不能仅凭请求参数把失败事件写进任意租户的审计范围。
        Long trustedTenant = "TENANT_SELECT".equals(stage) && resolvedAccount == null
                ? null : selectedTenant;
        T result;
        try {
            result = action.get();
        } catch (RuntimeException error) {
            record(resolvedAccount, trustedTenant, "LOGIN", "管理端登录失败", "FAILURE",
                    error.getClass().getSimpleName(), null);
            throw error;
        }
        boolean success = Result.CODE_SUCCESS.equals(result.getCode());
        boolean finalLogin = success && result.getData() instanceof SysUserVO;
        SysUserVO user = finalLogin ? (SysUserVO) result.getData() : null;
        String type = finalLogin || !success ? "LOGIN" : stage;
        String summary = finalLogin ? "管理端登录成功" : success ? "管理端身份验证通过" : "管理端登录失败";
        record(resolvedAccount, trustedTenant, type, summary, success ? "SUCCESS" : "FAILURE",
                success ? null : "身份验证或商户选择失败", user);
        return result;
    }

    /** 已验证阶段只用短时凭证查账号快照，凭证和动态口令均不进入审计事件。 */
    private String accountFromTicket(String stage, Long selectedTenant) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            return null;
        HttpServletRequest request = attributes.getRequest();
        String parameter = "MFA_VERIFY".equals(stage) ? "challengeTicket"
                : "TENANT_SELECT".equals(stage) ? "selectionTicket" : null;
        if (parameter == null) return null;
        String ticket = request.getParameter(parameter);
        if (ticket == null || !ticket.matches("[a-fA-F0-9]{32}")) return null;
        try {
            Long userId;
            if ("MFA_VERIFY".equals(stage)) {
                Object payload = redis.getCacheObject("admin:login:totp-challenge:" + ticket);
                if (payload == null) return null;
                List<Long> ids = JSON.parseArray(payload.toString(), Long.class);
                userId = ids == null || ids.isEmpty() ? null : ids.getFirst();
            } else {
                Object payload = redis.getCacheObject("admin:login:tenant-selection:" + ticket);
                if (payload == null || selectedTenant == null) return null;
                JSONObject mapping = JSON.parseObject(payload.toString());
                userId = mapping == null ? null : mapping.getLong(String.valueOf(selectedTenant));
            }
            if (userId == null) return null;
            SysUser user = TenantIgnore.get(() -> users.selectById(userId));
            return user == null ? null : user.getUsername();
        } catch (RuntimeException ignored) {
            return null;
        }
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
