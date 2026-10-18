package org.hongtu.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 超级管理员编辑的平台服务配置；已有密钥只回传已配置状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformServiceConfigDTO extends PlatformServiceRuntimeDTO {
    private Boolean mailPasswordConfigured;
    private Boolean smsAppSecretConfigured;
    private Boolean smsAppcodeConfigured;
    private Boolean qiniuAccessKeyConfigured;
    private Boolean qiniuSecretKeyConfigured;
    private Boolean minioAccessKeyConfigured;
    private Boolean minioSecretKeyConfigured;
    private Boolean ossAccessKeyConfigured;
    private Boolean ossSecretKeyConfigured;
    /** 显式清除旧密钥；空输入只表示保持原值。 */
    private Boolean clearMailPassword;
    private Boolean clearSmsAppSecret;
    private Boolean clearSmsAppcode;
    private Boolean clearQiniuAccessKey;
    private Boolean clearQiniuSecretKey;
    private Boolean clearMinioAccessKey;
    private Boolean clearMinioSecretKey;
    private Boolean clearOssAccessKey;
    private Boolean clearOssSecretKey;
}
