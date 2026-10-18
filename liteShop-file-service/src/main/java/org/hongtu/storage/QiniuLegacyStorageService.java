package org.hongtu.storage;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.enums.StorageTypeEnum;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/** 兼容现有七牛文件记录的历史驱动。 */
@Component
public class QiniuLegacyStorageService implements StorageService {
    @Override public StorageTypeEnum type() { return StorageTypeEnum.QINIU; }
    private Auth auth(PlatformServiceRuntimeDTO config) {
        return Auth.create(config.getQiniuAccessKey(), config.getQiniuSecretKey());
    }
    @Override public void upload(PlatformServiceRuntimeDTO config, String bucket, String key,
                                 InputStream stream, long size, String mimeType) throws Exception {
        new UploadManager(Configuration.create(Region.autoRegion())).put(stream, key,
                auth(config).uploadToken(bucket), null, mimeType);
    }
    @Override public void delete(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        new BucketManager(auth(config), Configuration.create(Region.autoRegion())).delete(bucket, key);
    }
    @Override public boolean exists(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        try { metadata(config, bucket, key); return true; }
        catch (com.qiniu.common.QiniuException error) {
            if (error.code() == 612) return false;
            throw error;
        }
    }
    @Override public StorageMetadata metadata(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        var stat = new BucketManager(auth(config), Configuration.create(Region.autoRegion())).stat(bucket, key);
        return new StorageMetadata(stat.fsize, stat.mimeType);
    }
    @Override public String publicUrl(PlatformServiceRuntimeDTO config, String key) {
        return config.getQiniuDomain().replaceAll("/+$", "") + "/" + key;
    }
    @Override public String signedUrl(PlatformServiceRuntimeDTO config, String bucket, String key, Duration ttl) {
        String base = bucket.equals(config.getQiniuPrivateBucket()) ? config.getQiniuPrivateDomain() : config.getQiniuDomain();
        return auth(config).privateDownloadUrl(base.replaceAll("/+$", "") + "/" + key, (int) ttl.toSeconds());
    }
    @Override public InputStream open(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        return new URL(signedUrl(config, bucket, key, Duration.ofMinutes(5))).openStream();
    }
    @Override public void copy(PlatformServiceRuntimeDTO config, String sourceBucket, String targetBucket, String key) throws Exception {
        new BucketManager(auth(config), Configuration.create(Region.autoRegion())).copy(sourceBucket, key, targetBucket, key);
    }
    @Override public void checkConnection(PlatformServiceRuntimeDTO config, String bucket) throws Exception {
        new BucketManager(auth(config), Configuration.create(Region.autoRegion())).listFiles(bucket, "", "", 1, "");
    }
}
