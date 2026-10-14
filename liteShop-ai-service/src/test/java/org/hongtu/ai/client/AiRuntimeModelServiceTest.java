package org.hongtu.ai.client;

import org.hongtu.Result.Result;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.hongtu.utils.AiEndpointGuard;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证替换 Key、URL、模型及关闭配置可直接作用于后续请求和多个实例。 */
class AiRuntimeModelServiceTest {
    private final ConfigFeignClient client = mock(ConfigFeignClient.class);

    @Test
    void changesAreAppliedOnNextRequestAcrossInstances() {
        var proxy = new AiProxyResolver(new MockEnvironment());
        var first = service(proxy);
        var second = service(proxy);
        when(client.aiModel()).thenReturn(Result.success(config(1, true, "fixture-key-one", "https://provider-a.example/v1", "model-one")));
        var old = first.currentModel();
        second.currentModel();
        when(client.aiModel()).thenReturn(Result.success(config(2, true, "fixture-key-two", "https://provider-b.example/v1", "model-two")));
        var updated = (OpenAiChatModel) first.currentModel();
        assertNotSame(old, updated);
        assertEquals("fixture-key-two", updated.getOptions().getApiKey());
        assertEquals("https://provider-b.example/v1", updated.getOptions().getBaseUrl());
        assertEquals("model-two", updated.getOptions().getModel());
        assertEquals("fixture-key-two", ((OpenAiChatModel) second.currentModel()).getOptions().getApiKey());
        assertSame(updated, first.currentModel());
    }

    @Test
    void disabledConfigurationCannotBeRevivedByDelayedOldReads() {
        var service = service(new AiProxyResolver(new MockEnvironment()));
        var old = config(1, true, "fixture-key", "https://provider.example/v1", "model-one");
        when(client.aiModel()).thenReturn(Result.success(old));
        assertNotNull(service.currentModel());
        when(client.aiModel()).thenReturn(Result.success(config(2, false, "", "", "")));
        assertNull(service.currentModel());
        when(client.aiModel()).thenReturn(Result.success(old));
        assertNull(service.currentModel());
        when(client.aiModel()).thenReturn(Result.success((AiModelRuntimeConfigDTO) null));
        assertNull(service.currentModel());
    }

    @Test
    void unsavedConfigurationHasNoModelAndServiceFailuresDoNotUseOldKeys() {
        var service = service(new AiProxyResolver(new MockEnvironment()));
        when(client.aiModel()).thenReturn(Result.success((AiModelRuntimeConfigDTO) null));
        assertNull(service.currentModel());
        when(client.aiModel()).thenThrow(new IllegalStateException("config offline"));
        assertThrows(IllegalStateException.class, service::currentModel);
    }

    @Test
    void standardProxyEnvironmentIsAppliedToDynamicOpenAiClient() {
        var environment = new MockEnvironment().withProperty("HTTPS_PROXY", "http://127.0.0.1:7897");
        var service = service(new AiProxyResolver(environment));
        when(client.aiModel()).thenReturn(Result.success(
                config(1, true, "fixture-key", "https://provider.example/v1", "fixture-model")));

        var options = ((OpenAiChatModel) service.currentModel()).getOptions();
        assertEquals(Proxy.Type.HTTP, options.getProxy().type());
        var address = (InetSocketAddress) options.getProxy().address();
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(7897, address.getPort());
    }

    @Test
    void privateOrPlaintextBaseUrlDoesNotCreateAModel() {
        var service = service(new AiProxyResolver(new MockEnvironment()));
        when(client.aiModel()).thenReturn(Result.success(config(1, true, "fixture-key", "http://localhost/v1", "model-one")));
        assertNull(service.currentModel());
    }

    private AiRuntimeModelService service(AiProxyResolver proxy) {
        AiEndpointGuard endpoints = new AiEndpointGuard(host -> new InetAddress[]{
                InetAddress.getByAddress(host, new byte[]{1, 1, 1, 1})});
        return new AiRuntimeModelService(client, proxy, endpoints);
    }

    private static AiModelRuntimeConfigDTO config(long revision, boolean enabled, String key, String url, String model) {
        var config = new AiModelRuntimeConfigDTO();
        config.setRevision(revision); config.setEnabled(enabled); config.setApiKey(key); config.setBaseUrl(url); config.setModel(model);
        return config;
    }
}
