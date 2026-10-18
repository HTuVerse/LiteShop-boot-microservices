package org.hongtu.storage;

import io.minio.*;
import io.minio.Http;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.enums.StorageTypeEnum;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;

/** MinIO S3 API 驱动；操作端点与浏览器签名地址分别配置。 */
@Component
public class MinioStorageService implements StorageService {
    @Override public StorageTypeEnum type() { return StorageTypeEnum.MINIO; }

    private MinioClient client(PlatformServiceRuntimeDTO config, boolean browser) {
        String endpoint = browser && config.getMinioBrowserEndpoint() != null
                && !config.getMinioBrowserEndpoint().isBlank() ? config.getMinioBrowserEndpoint() : config.getMinioEndpoint();
        return MinioClient.builder().endpoint(endpoint)
                .credentials(config.getMinioAccessKey(), config.getMinioSecretKey()).build();
    }
    @Override public void upload(PlatformServiceRuntimeDTO config, String bucket, String key,
                                 InputStream stream, long size, String mimeType) throws Exception {
        client(config, false).putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(stream, size, -1).contentType(mimeType).build());
    }
    @Override public void delete(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        client(config, false).removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    }
    @Override public boolean exists(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        try { metadata(config, bucket, key); return true; }
        catch (io.minio.errors.ErrorResponseException error) {
            if ("NoSuchKey".equals(error.errorResponse().code())) return false;
            throw error;
        }
    }
    @Override public StorageMetadata metadata(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        StatObjectResponse result = client(config, false).statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
        return new StorageMetadata(result.size(), result.contentType());
    }
    @Override public String publicUrl(PlatformServiceRuntimeDTO config, String key) {
        return config.getMinioPublicBaseUrl().replaceAll("/+$", "") + "/" + key;
    }
    @Override public String signedUrl(PlatformServiceRuntimeDTO config, String bucket, String key, Duration ttl) throws Exception {
        return client(config, true).getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Http.Method.GET).bucket(bucket).object(key).expiry((int) ttl.toSeconds()).build());
    }
    @Override public InputStream open(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        return client(config, false).getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
    }
    @Override public void copy(PlatformServiceRuntimeDTO config, String sourceBucket, String targetBucket, String key) throws Exception {
        client(config, false).copyObject(CopyObjectArgs.builder().bucket(targetBucket).object(key)
                .source(CopySource.builder().bucket(sourceBucket).object(key).build()).build());
    }
    @Override public void checkConnection(PlatformServiceRuntimeDTO config, String bucket) throws Exception {
        if (!client(config, false).bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
            throw new IllegalStateException("MinIO Bucket 不存在：" + bucket);
    }
}
