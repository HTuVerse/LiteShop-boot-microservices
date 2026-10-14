package org.hongtu.config.DTO;
import lombok.Data;
import lombok.ToString;
/** 平台 AI 模型持久化配置，密钥只保存密文。 */
@Data
public class AiModelConfigRecord {
    private Long revision;
    private Boolean enabled;
    private String baseUrl;
    private String model;
    @ToString.Exclude private String encryptedApiKey;
}
