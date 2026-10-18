package org.hongtu.api.dto;

import lombok.Data;
import lombok.ToString;

/** 仅供内部服务读取的平台邮件、短信与文件运行配置。 */
@Data
public class PlatformServiceRuntimeDTO {
    private Long revision;
    private String websiteName;
    private String mailHost;
    private Integer mailPort;
    private String mailUsername;
    @ToString.Exclude private String mailPassword;
    private String mailFrom;
    private String mailProtocol;
    private String smsAppKey;
    @ToString.Exclude private String smsAppSecret;
    private String smsTemplateId;
    @ToString.Exclude private String smsAppcode;
    private String smsHost;
    private Boolean ossEnabled;
    /** LOCAL/MINIO/OSS/QINIU；旧配置缺省时按 ossEnabled 映射到 QINIU。 */
    private String storageMode;
    private Long profileId;
    private String uploadPath;
    private String baseUrl;
    private String allowedTypes;
    private Integer maxSize;
    @ToString.Exclude private String qiniuAccessKey;
    @ToString.Exclude private String qiniuSecretKey;
    private String qiniuBucket;
    private String qiniuDomain;
    private String qiniuPrivateBucket;
    private String qiniuPrivateDomain;
    private String minioEndpoint;
    private String minioBrowserEndpoint;
    private String minioPublicBucket;
    private String minioPrivateBucket;
    private String minioPublicBaseUrl;
    @ToString.Exclude private String minioAccessKey;
    @ToString.Exclude private String minioSecretKey;
    private String ossEndpoint;
    private String ossPublicBucket;
    private String ossPrivateBucket;
    private String ossPublicBaseUrl;
    @ToString.Exclude private String ossAccessKey;
    @ToString.Exclude private String ossSecretKey;
}
