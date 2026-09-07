package org.hongtu.tenant;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;

/** MQ 业务消息必须携带租户，监听器线程不得继承上一条消息的租户或权限范围。 */
public class TenantMqAdvice implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        clearScope();
        try {
            TenantContext.setTenantId(extractTenantId(invocation.getArguments()));
            return invocation.proceed();
        } finally {
            clearScope();
        }
    }

    private Long extractTenantId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Message message) {
                Object value = message.getMessageProperties().getHeader(TenantConstants.MQ_TENANT_HEADER);
                try {
                    long tenantId = Long.parseLong(String.valueOf(value));
                    if (tenantId > 0) {
                        return tenantId;
                    }
                } catch (NumberFormatException ignored) {
                    // 缺失、非数字与非正数统一拒绝，不执行消费业务。
                }
            }
        }
        throw new IllegalStateException("MQ消息缺少有效租户上下文");
    }

    private void clearScope() {
        TenantIgnore.suspend();
        TenantContext.clear();
        StoreContext.clear();
    }
}