package org.hongtu.audit;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hongtu.Result.Result;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/** 统一采集标记过的后台操作，并区分业务返回失败与抛异常。 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    private final AuditOutbox outbox;
    private final AuditEvents events;

    /** 对尚未逐项标注的受保护后台写接口保留基础审计覆盖。 */
    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public Object protectedWrite(ProceedingJoinPoint invocation) throws Throwable {
        Method method = ((MethodSignature) invocation.getSignature()).getMethod();
        if (method.isAnnotationPresent(AuditOperation.class) || !protectedAdminMethod(method))
            return invocation.proceed();
        List<String> roles;
        try {
            if (!StpUtil.isLogin()) return invocation.proceed();
            roles = StpUtil.getRoleList();
        } catch (RuntimeException ignored) {
            return invocation.proceed();
        }
        if (roles == null || roles.stream().noneMatch(role -> !"USER".equals(role)))
            return invocation.proceed();
        outbox.requireReady();
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        String module = permission != null && permission.value().length > 0
                ? permission.value()[0] : method.getDeclaringClass().getSimpleName();
        AuditEvent base = events.create(null, module, "WRITE", method.getDeclaringClass().getSimpleName(),
                null, method.getName(), "PENDING", null, null, null);
        try {
            Object response;
            try {
                response = invocation.proceed();
            } catch (Throwable error) {
                write(base, outcome(error), error.getClass().getSimpleName());
                throw error;
            }
            boolean successful = !(response instanceof Result<?> result)
                    || Result.CODE_SUCCESS.equals(result.getCode());
            write(base, successful ? "SUCCESS" : "FAILURE", successful ? null : "业务返回失败");
            return response;
        } finally {
            AuditContext.clear();
        }
    }

    private boolean protectedAdminMethod(Method method) {
        return method.isAnnotationPresent(SaCheckPermission.class)
                || method.isAnnotationPresent(SaCheckRole.class)
                || method.getDeclaringClass().isAnnotationPresent(SaCheckPermission.class)
                || method.getDeclaringClass().isAnnotationPresent(SaCheckRole.class);
    }

    private String outcome(Throwable error) {
        return error instanceof NotPermissionException || error instanceof NotRoleException
                || error instanceof SecurityException ? "DENIED" : "FAILURE";
    }

    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint invocation, AuditOperation operation) throws Throwable {
        // 退出接口同时服务顾客端，仅后台角色写入管理操作日志。
        if ("LOGOUT".equals(operation.type()) && !adminSession()) return invocation.proceed();
        outbox.requireReady();
        Object[] arguments = invocation.getArgs();
        String objectId = argumentId(arguments, operation.objectIdArgument());
        // 注销或切换工作空间会改变会话，必须在执行业务前固定操作人快照。
        AuditEvent base = events.create(null, operation.module(), operation.type(),
                operation.objectType(), objectId, operation.summary(), "PENDING", null, null, null);
        try {
            Object response;
            try {
                response = invocation.proceed();
            } catch (Throwable error) {
                write(base, outcome(error), error.getClass().getSimpleName());
                throw error;
            }
            boolean successful = !(response instanceof Result<?> result)
                    || Result.CODE_SUCCESS.equals(result.getCode());
            write(base, successful ? "SUCCESS" : "FAILURE",
                    successful ? null : "业务返回失败");
            return response;
        } finally {
            AuditContext.clear();
        }
    }

    private boolean adminSession() {
        try {
            return StpUtil.isLogin() && StpUtil.getRoleList().stream()
                    .anyMatch(role -> !"USER".equals(role));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void write(AuditEvent base, String result, String message) {
        AuditContext.Details details = AuditContext.details();
        AuditEvent event = new AuditEvent(base.eventId(), base.occurredAt(), base.actorUserId(),
                base.actorAccount(), base.actorTenantId(), base.scopeTenantId(),
                details.subjectTenantId == null ? base.subjectTenantId() : details.subjectTenantId,
                base.storeId(), base.clientIp(), base.serviceName(), base.moduleCode(),
                base.operationType(), base.objectType(),
                details.objectId == null ? base.objectId() : details.objectId,
                details.summary == null ? base.summary() : details.summary,
                result, message, base.requestId(), base.requestPath(), details.diffJson);
        outbox.append(event);
    }

    private String argumentId(Object[] arguments, int index) {
        if (index < 0 || index >= arguments.length || arguments[index] == null) return null;
        Object value = arguments[index];
        if (value instanceof Number || value instanceof String) return String.valueOf(value);
        try {
            Object id = value.getClass().getMethod("getId").invoke(value);
            return id == null ? null : String.valueOf(id);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
