package org.hongtu.config.service;

import org.hongtu.api.dto.TenantPaymentAccountView;
import org.hongtu.config.DAO.TenantPaymentAccountMapper;
import org.hongtu.config.DTO.TenantPaymentAccount;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.StoreContext;
import org.hongtu.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 支付账户只对租户管理员可见，密钥加密后不回显。 */
class TenantPaymentAccountServiceTest {
    private final TenantPaymentAccountMapper mapper = mock(TenantPaymentAccountMapper.class);
    private final AiModelKeyCipher cipher = new AiModelKeyCipher("fixture-encryption-secret");
    private final TenantPaymentAccountService service = new TenantPaymentAccountService(mapper, cipher);
    private final List<TenantPaymentAccount> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(8L);
        StoreContext.clear();
        stored.clear();
        when(mapper.selectOne(any())).thenAnswer(invocation -> stored.stream().findFirst().orElse(null));
        when(mapper.insert(any(TenantPaymentAccount.class))).thenAnswer(invocation -> {
            stored.add(invocation.getArgument(0));
            return 1;
        });
        when(mapper.updateById(any(TenantPaymentAccount.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        StoreContext.clear();
    }

    @Test
    void storeSessionCannotReadOrSave() {
        StoreContext.setRestricted(true, List.of(3L));
        assertEquals(403, assertThrows(BusinessException.class, service::listForEdit).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.save(wechat("secret-key"), 1L)).getCode());
        verify(mapper, never()).insert(any(TenantPaymentAccount.class));
    }

    @Test
    void secretsAreEncryptedAndNotReturned() {
        TenantPaymentAccountView saved = service.save(wechat("plain-private-key"), 9L);
        assertNull(saved.getPrivateKey());
        assertTrue(saved.isPrivateKeyConfigured());
        assertFalse(stored.get(0).getPrivateKeyCiphertext().contains("plain-private-key"));
        assertEquals("plain-private-key", cipher.decrypt(stored.get(0).getPrivateKeyCiphertext()));
        var runtime = service.runtime(8L, "WECHAT");
        assertEquals("plain-private-key", runtime.getPrivateKey());
        assertEquals("wx-app", runtime.getAppId());
    }

    @Test
    void missingAccountIsNotUsableAndOtherTenantIsRejected() {
        assertNull(service.runtime(8L, "ALIPAY"));
        TenantContext.setTenantId(9L);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.runtime(8L, "WECHAT")).getCode());
    }

    @Test
    void saveWeChatWithV2KeyThenRuntimeReturnsDecrypted() {
        TenantPaymentAccountView view = wechat("plain-private-key");
        view.setApiV2Key("12345678901234567890123456789012");
        TenantPaymentAccountView saved = service.save(view, 9L);
        assertNull(saved.getApiV2Key());
        assertTrue(saved.isApiV2KeyConfigured());

        var runtime = service.runtime(8L, "WECHAT");
        assertEquals("12345678901234567890123456789012", runtime.getApiV2Key());
    }

    @Test
    void saveWeChatWithoutV2KeyKeepsExistingCiphertext() {
        TenantPaymentAccountView first = wechat("plain-private-key");
        first.setApiV2Key("12345678901234567890123456789012");
        service.save(first, 9L);

        // 二次保存不传 v2 key，不应清空既有密文
        TenantPaymentAccountView again = service.save(wechat("plain-private-key"), 9L);
        assertTrue(again.isApiV2KeyConfigured());
        assertEquals("12345678901234567890123456789012",
                cipher.decrypt(stored.get(0).getApiV2KeyCiphertext()));
    }

    private TenantPaymentAccountView wechat(String privateKey) {
        TenantPaymentAccountView view = new TenantPaymentAccountView();
        view.setChannel("WECHAT");
        view.setEnabled(true);
        view.setAppId("wx-app");
        view.setMchId("mch-1");
        view.setMchSerialNo("serial-1");
        view.setPrivateKey(privateKey);
        view.setApiV3Key("0123456789abcdef0123456789abcdef");
        return view;
    }
}
