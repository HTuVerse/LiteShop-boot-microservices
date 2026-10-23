package org.hongtu.audit;

/** 跨服务传输的脱敏审计事件；来源服务不能指定校验链字段。 */
public record AuditEvent(
        String eventId,
        String occurredAt,
        Long actorUserId,
        String actorAccount,
        Long actorTenantId,
        Long scopeTenantId,
        Long subjectTenantId,
        Long storeId,
        String clientIp,
        String serviceName,
        String moduleCode,
        String operationType,
        String objectType,
        String objectId,
        String summary,
        String result,
        String resultMessage,
        String requestId,
        String requestPath,
        String diffJson
) { }
