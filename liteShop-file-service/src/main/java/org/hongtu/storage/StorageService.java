package org.hongtu.storage;

import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.enums.StorageTypeEnum;

import java.io.InputStream;
import java.time.Duration;

/** 文件服务唯一的存储驱动接口，业务层不直接调用厂商 SDK。 */
public interface StorageService {
    StorageTypeEnum type();
    void upload(PlatformServiceRuntimeDTO config, String bucket, String key, InputStream stream,
                long size, String mimeType) throws Exception;
    void delete(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception;
    boolean exists(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception;
    StorageMetadata metadata(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception;
    String publicUrl(PlatformServiceRuntimeDTO config, String key);
    String signedUrl(PlatformServiceRuntimeDTO config, String bucket, String key, Duration ttl) throws Exception;
    InputStream open(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception;
    void copy(PlatformServiceRuntimeDTO config, String sourceBucket, String targetBucket, String key) throws Exception;
    void checkConnection(PlatformServiceRuntimeDTO config, String bucket) throws Exception;

    /** 存储层返回的基础元数据。 */
    record StorageMetadata(long size, String mimeType) { }
}
