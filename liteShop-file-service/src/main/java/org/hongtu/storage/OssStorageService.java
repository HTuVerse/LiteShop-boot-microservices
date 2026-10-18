package org.hongtu.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.enums.StorageTypeEnum;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Date;

/** 阿里云 OSS 原生 SDK 驱动，不依赖 S3 兼容接口。 */
@Component
public class OssStorageService implements StorageService {
    @Override public StorageTypeEnum type() { return StorageTypeEnum.OSS; }
    private OSS client(PlatformServiceRuntimeDTO config) {
        return new OSSClientBuilder().build(config.getOssEndpoint(), config.getOssAccessKey(), config.getOssSecretKey());
    }
    @Override public void upload(PlatformServiceRuntimeDTO config, String bucket, String key,
                                 InputStream stream, long size, String mimeType) {
        OSS client = client(config);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(mimeType);
            client.putObject(bucket, key, stream, metadata);
        } finally { client.shutdown(); }
    }
    @Override public void delete(PlatformServiceRuntimeDTO config, String bucket, String key) {
        OSS client = client(config);
        try { client.deleteObject(bucket, key); } finally { client.shutdown(); }
    }
    @Override public boolean exists(PlatformServiceRuntimeDTO config, String bucket, String key) {
        OSS client = client(config);
        try { return client.doesObjectExist(bucket, key); } finally { client.shutdown(); }
    }
    @Override public StorageMetadata metadata(PlatformServiceRuntimeDTO config, String bucket, String key) {
        OSS client = client(config);
        try {
            ObjectMetadata metadata = client.getObjectMetadata(bucket, key);
            return new StorageMetadata(metadata.getContentLength(), metadata.getContentType());
        } finally { client.shutdown(); }
    }
    @Override public String publicUrl(PlatformServiceRuntimeDTO config, String key) {
        return config.getOssPublicBaseUrl().replaceAll("/+$", "") + "/" + key;
    }
    @Override public String signedUrl(PlatformServiceRuntimeDTO config, String bucket, String key, Duration ttl) {
        OSS client = client(config);
        try { return client.generatePresignedUrl(bucket, key, new Date(System.currentTimeMillis() + ttl.toMillis())).toString(); }
        finally { client.shutdown(); }
    }
    @Override public InputStream open(PlatformServiceRuntimeDTO config, String bucket, String key) {
        OSS client = client(config);
        InputStream stream;
        try { stream = client.getObject(bucket, key).getObjectContent(); }
        catch (RuntimeException error) { client.shutdown(); throw error; }
        return new FilterInputStream(stream) {
            @Override public void close() throws java.io.IOException {
                try { super.close(); } finally { client.shutdown(); }
            }
        };
    }
    @Override public void copy(PlatformServiceRuntimeDTO config, String sourceBucket, String targetBucket, String key) {
        OSS client = client(config);
        try { client.copyObject(sourceBucket, key, targetBucket, key); }
        finally { client.shutdown(); }
    }
    @Override public void checkConnection(PlatformServiceRuntimeDTO config, String bucket) {
        OSS client = client(config);
        try { if (!client.doesBucketExist(bucket)) throw new IllegalStateException("OSS Bucket 不存在：" + bucket); }
        finally { client.shutdown(); }
    }
}
