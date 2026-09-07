package org.hongtu.Service.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.hongtu.utils.EmailUtil;
import org.hongtu.utils.RedisCache;
import org.hongtu.utils.SmsSendUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageDeliveryTest {
    private MessageServiceImpl service(EmailUtil email, SmsSendUtil sms, RedisCache redis) {
        MessageServiceImpl service = new MessageServiceImpl();
        ReflectionTestUtils.setField(service, "emailUtil", email);
        ReflectionTestUtils.setField(service, "smsSendUtil", sms);
        ReflectionTestUtils.setField(service, "redisCache", redis);
        return service;
    }

    @Test void unconfiguredChannelsDoNotSendOrCacheCodes() {
        EmailUtil email = mock(EmailUtil.class);
        SmsSendUtil sms = mock(SmsSendUtil.class);
        RedisCache redis = mock(RedisCache.class);
        MessageServiceImpl service = service(email, sms, redis);
        assertEquals(503, service.sendEmailVerifyCode("1001", "test@example.invalid", "otp:", "login").getCode());
        assertEquals(503, service.sendSmsVerifyCode("1001", "13800000000", "otp:").getCode());
        verify(email, never()).sendHtmlMail(any(), any(), any());
        verify(sms, never()).sendVerificationCode(any(), any());
        verifyNoInteractions(redis);
    }

    @Test void failedMailIsNotCachedAsSuccessfulDelivery() {
        EmailUtil email = mock(EmailUtil.class);
        RedisCache redis = mock(RedisCache.class);
        when(email.isConfigured()).thenReturn(true);
        when(email.getHtmlEmailContent(anyString())).thenReturn("body");
        doThrow(new IllegalStateException("delivery failed")).when(email).sendHtmlMail(any(), any(), any());
        assertNotEquals(200, service(email, mock(SmsSendUtil.class), redis)
                .sendEmailVerifyCode("1001", "test@example.invalid", "otp:", "login").getCode());
        verifyNoInteractions(redis);
    }

    @Test void htmlMailPropagatesDeliveryFailure() {
        EmailUtil email = new EmailUtil();
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("test failure")).when(sender).send(any(MimeMessage.class));
        ReflectionTestUtils.setField(email, "mailSender", sender);
        ReflectionTestUtils.setField(email, "from", "sender@example.invalid");
        assertThrows(IllegalStateException.class, () -> email.sendHtmlMail("test@example.invalid", "login", "body"));
    }

    @Test void deliveredCodesKeepSeparateTenantCacheKeys() {
        SmsSendUtil sms = mock(SmsSendUtil.class);
        RedisCache redis = mock(RedisCache.class);
        when(sms.isConfigured()).thenReturn(true);
        when(sms.sendVerificationCode(anyString(), anyString())).thenReturn(true);
        MessageServiceImpl service = service(mock(EmailUtil.class), sms, redis);
        assertEquals(200, service.sendSmsVerifyCode("1001", "13800000000", "otp:").getCode());
        assertEquals(200, service.sendSmsVerifyCode("1002", "13800000000", "otp:").getCode());
        verify(redis).setCacheObject(eq("otp:1001:13800000000"), anyString(), anyInt(), eq(TimeUnit.MINUTES));
        verify(redis).setCacheObject(eq("otp:1002:13800000000"), anyString(), anyInt(), eq(TimeUnit.MINUTES));
    }
}
