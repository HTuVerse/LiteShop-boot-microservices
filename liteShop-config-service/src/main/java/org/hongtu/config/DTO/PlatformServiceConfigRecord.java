package org.hongtu.config.DTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;

/** 平台服务配置持久化模型，敏感字段仅保存密文。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformServiceConfigRecord extends PlatformServiceRuntimeDTO {
    private String encryptedMailPassword = "";
    private String encryptedSmsAppSecret = "";
    private String encryptedSmsAppcode = "";
    private String encryptedQiniuAccessKey = "";
    private String encryptedQiniuSecretKey = "";
    private String encryptedMinioAccessKey = "";
    private String encryptedMinioSecretKey = "";
    private String encryptedOssAccessKey = "";
    private String encryptedOssSecretKey = "";
}
