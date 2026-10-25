package org.hongtu.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 审计签名当前密钥及历史版本密钥；历史密钥只用于校验旧记录。 */
@Data
@Component
@ConfigurationProperties(prefix = "liteshop.audit")
public class AuditKeyProperties {
    private String hmacKey;
    private String keyVersion = "v1";
    private Map<String, String> historicalKeys = new HashMap<>();
}
