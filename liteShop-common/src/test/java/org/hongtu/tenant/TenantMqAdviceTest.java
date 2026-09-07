package org.hongtu.tenant;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantMqAdviceTest {

    private final TenantMqAdvice advice = new TenantMqAdvice();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRestoreAndClearTenantContext() throws Throwable {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(TenantConstants.MQ_TENANT_HEADER, 1001L);
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{new Message(new byte[0], properties)});
        when(invocation.proceed()).thenAnswer(ignored -> {
            assertEquals(1001L, TenantContext.getTenantId());
            return "ok";
        });

        assertEquals("ok", advice.invoke(invocation));
        assertNull(TenantContext.getTenantIdOrNull());
        verify(invocation).proceed();
    }

    @Test
    void shouldRejectMessageWithoutTenantHeader() {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{new Message(new byte[0])});

        assertThrows(IllegalStateException.class, () -> advice.invoke(invocation));
        assertNull(TenantContext.getTenantIdOrNull());
    }
}
