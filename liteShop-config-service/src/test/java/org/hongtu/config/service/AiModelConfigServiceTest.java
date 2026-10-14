package org.hongtu.config.service;

import org.hongtu.api.dto.AiModelConfigDTO;
import org.hongtu.config.DAO.AiModelConfigMapper;
import org.hongtu.config.DTO.AiModelConfigRecord;
import org.hongtu.exception.BusinessException;
import org.hongtu.utils.AiEndpointGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证保存、密钥保留与替换、清除和并发版本校验。 */
class AiModelConfigServiceTest {
    private final AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
    private final AiModelKeyCipher cipher = new AiModelKeyCipher("fixture-encryption-secret");
    private final AiEndpointGuard endpoints = publicEndpoints();
    private final AiModelConfigService service = new AiModelConfigService(mapper, cipher, endpoints);
    private AiModelConfigRecord stored;

    @BeforeEach
    void setUp() {
        stored = new AiModelConfigRecord();
        stored.setRevision(0L); stored.setEnabled(false); stored.setBaseUrl("https://api.openai.com/v1"); stored.setModel(""); stored.setEncryptedApiKey("");
        when(mapper.read()).thenAnswer(invocation -> copy(stored));
        when(mapper.save(any(), anyLong())).thenAnswer(invocation -> {
            stored = copy(invocation.getArgument(0)); stored.setRevision(stored.getRevision() + 1); return 1;
        });
    }

    @Test
    void keysAreEncryptedNotEchoedAndRetainedWhenInputIsEmpty() {
        assertNull(service.getRuntime());
        var input = new AiModelConfigDTO();
        input.setEnabled(true); input.setApiKey("fixture-key-one"); input.setBaseUrl("https://example.com/"); input.setModel("model-one");
        var saved = service.save(input, 7);
        assertEquals(1L, saved.getRevision()); assertNull(saved.getApiKey()); assertTrue(saved.isKeyConfigured());
        assertEquals("https://example.com/v1", saved.getBaseUrl());
        assertFalse(stored.getEncryptedApiKey().contains("fixture-key-one"));
        assertEquals("fixture-key-one", service.getRuntime().getApiKey());
        saved.setBaseUrl("https://other.example/v1"); saved.setModel("model-two");
        var retained = service.save(saved, 7);
        assertEquals("fixture-key-one", service.getRuntime().getApiKey());
        retained.setApiKey("fixture-key-two");
        var replaced = service.save(retained, 7);
        assertNull(replaced.getApiKey()); assertNull(service.getForEdit().getApiKey());
        assertEquals("fixture-key-two", service.getRuntime().getApiKey());
    }

    @Test
    void clearRequiresDisablingAndReenableRequiresAKey() {
        var input = new AiModelConfigDTO(); input.setEnabled(true); input.setModel("model-one"); input.setApiKey("fixture-key");
        var saved = service.save(input, 7);
        saved.setClearApiKey(true);
        assertThrows(BusinessException.class, () -> service.save(saved, 7));
        saved.setEnabled(false);
        var cleared = service.save(saved, 7);
        assertFalse(cleared.isKeyConfigured()); assertFalse(service.getRuntime().getEnabled()); assertEquals("", service.getRuntime().getApiKey());
        cleared.setEnabled(true);
        assertThrows(BusinessException.class, () -> service.save(cleared, 7));
    }

    @Test
    void staleAndRacingUpdatesAreRejected() {
        stored.setRevision(3L);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.save(new AiModelConfigDTO(), 7)).getCode());
        verify(mapper, never()).save(any(), anyLong());
        var input = service.getForEdit();
        doReturn(0).when(mapper).save(any(), anyLong());
        assertEquals(409, assertThrows(BusinessException.class, () -> service.save(input, 7)).getCode());
    }

    @Test
    void credentialsAndChatPathsAreRejectedAndCipherRejectsTampering() {
        for (String url : new String[]{"ftp://example.com", "http://api.example.com", "https://localhost:8000",
                "https://user:secret@example.com", "https://example.com/v1/chat/completions", "https://example.com?key=secret"})
            assertThrows(BusinessException.class, () -> endpoints.normalize(url));
        assertEquals("https://example.com/v1", endpoints.normalize("https://example.com"));
        AiEndpointGuard privateAddresses = new AiEndpointGuard(host -> new InetAddress[]{
                InetAddress.getByAddress(host, new byte[]{10, 1, 2, 3})});
        assertThrows(BusinessException.class, () -> privateAddresses.normalize("https://internal.example/v1"));
        String encrypted = cipher.encrypt("fixture-key");
        assertEquals("fixture-key", cipher.decrypt(encrypted));
        assertNotEquals(encrypted, cipher.encrypt("fixture-key"));
        assertThrows(BusinessException.class, () -> new AiModelKeyCipher("other-secret").decrypt(encrypted));
        assertThrows(BusinessException.class, () -> new AiModelKeyCipher("").encrypt("fixture-key"));
    }

    private static AiEndpointGuard publicEndpoints() {
        return new AiEndpointGuard(host -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{1, 1, 1, 1})});
    }

    private static AiModelConfigRecord copy(AiModelConfigRecord input) {
        var result = new AiModelConfigRecord();
        result.setRevision(input.getRevision()); result.setEnabled(input.getEnabled()); result.setBaseUrl(input.getBaseUrl()); result.setModel(input.getModel()); result.setEncryptedApiKey(input.getEncryptedApiKey());
        return result;
    }
}
