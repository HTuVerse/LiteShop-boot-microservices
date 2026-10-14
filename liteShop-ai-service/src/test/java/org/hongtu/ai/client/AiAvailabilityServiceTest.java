package org.hongtu.ai.client;

import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 按平台管理员和普通账号验证配置、额度、限流提示。 */
class AiAvailabilityServiceTest {
    @Test
    void connectionTestRequiresBusinessStructuredOutput() {
        var models = mock(AiRuntimeModelService.class);
        ChatModel model = mock(ChatModel.class);
        when(models.testModel(any())).thenReturn(model);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("""
                        {"keyPoints":[{"title":"正常","detail":"正常"}],
                        "evidence":[{"level":"INFO","title":"测试","detail":"测试"}],
                        "todayTasks":[{"title":"检查","description":"检查","risk":"无"}]}
                        """)))));

        var result = new AiAvailabilityService(models).testConnection(new AiModelRuntimeConfigDTO());

        assertTrue(result.connected());
        verify(model).call(argThat((Prompt prompt) -> prompt.getOptions().getMaxTokens() == 512));
    }

    @Test
    @SuppressWarnings("unchecked")
    void administratorReceivesSpecificGuidanceWithoutProviderSecrets() {
        try (var roles = mockStatic(StpUtil.class)) {
            roles.when(() -> StpUtil.hasRole("SUPER_ADMIN")).thenReturn(true);
            var models = mock(AiRuntimeModelService.class);
            var service = new AiAvailabilityService(models);
            assertEquals("NOT_CONFIGURED", service.status().reason());
            assertTrue(service.status().message().contains("AI 模型配置"));
            assertTrue(service.modelFailure(new RuntimeException("insufficient_quota secret-key")).getMessage().contains("额度"));
            var invalidKey = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "secret-key", HttpHeaders.EMPTY, new byte[0], null);
            assertTrue(service.modelFailure(invalidKey).getMessage().contains("Key 无效"));
            assertFalse(service.modelFailure(invalidKey).getMessage().contains("secret-key"));
            var limited = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limited", HttpHeaders.EMPTY, new byte[0], null);
            assertTrue(service.modelFailure(limited).getMessage().contains("限流"));
            assertEquals("NETWORK_UNAVAILABLE", service.classifyModelFailure(
                    new ResourceAccessException("I/O error", new ConnectException("Connection refused"))));
            assertTrue(service.modelFailure(new RuntimeException("connect timeout")).getMessage().contains("代理"));
            assertEquals("INVALID_MODEL_RESPONSE", service.classifyModelFailure(
                    new RuntimeException("Failed to convert model output: invalid JSON")));
            assertTrue(service.modelFailure(
                    new RuntimeException("Failed to convert model output: invalid JSON"))
                    .getMessage().contains("结构化格式"));
            when(models.currentModel()).thenReturn(mock(ChatModel.class));
            assertTrue(service.status().enabled());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryRolesOnlySeeTemporarilyUnavailable() {
        try (var roles = mockStatic(StpUtil.class)) {
            var service = new AiAvailabilityService(mock(AiRuntimeModelService.class));
            assertFalse(service.status().canConfigure());
            assertEquals("UNAVAILABLE", service.status().reason());
            for (String detail : new String[]{"invalid_api_key", "insufficient_quota", "429 rate_limit", "timeout"}) {
                var error = service.modelFailure(new RuntimeException(detail));
                // 限流和网络故障保持 429/502，避免前端把瞬时故障当成模型永久关闭。
                int expected = switch (detail) {
                    case "429 rate_limit" -> 429;
                    case "timeout" -> 502;
                    default -> 503;
                };
                assertEquals(expected, error.getCode());
                assertTrue(error.getMessage().contains("暂不可用"));
                assertFalse(error.getMessage().contains("Key"));
                assertFalse(error.getMessage().contains("配置中心"));
            }
        }
    }
}
