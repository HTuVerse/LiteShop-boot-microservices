package org.hongtu.tenant;

/**
 * 租户可访问状态校验器（启用、未到期且具备有效套餐）。
 * <p>
 * user-service 使用本地数据库实现，其他业务服务通过内部 Feign 接口查询
 * user-service，确保匿名公开请求和既有登录会话都不能继续访问已停用/到期租户。
 */
@FunctionalInterface
public interface TenantAccessValidator {

    boolean isActive(Long tenantId);
}
