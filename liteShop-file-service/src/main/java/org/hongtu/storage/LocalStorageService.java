package org.hongtu.storage;

import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.enums.StorageTypeEnum;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** 本地持久目录驱动，私有文件位于不对外映射的 .private 子目录。 */
@Component
public class LocalStorageService implements StorageService {
    @Override public StorageTypeEnum type() { return StorageTypeEnum.LOCAL; }

    private Path path(PlatformServiceRuntimeDTO config, String bucket, String key) {
        if (!key.matches("(?i)[0-9]{8}/[a-f0-9]{32}\\.[a-z0-9]{1,10}"))
            throw new IllegalArgumentException("文件存储键不合法");
        Path root = Path.of(config.getUploadPath()).toAbsolutePath().normalize();
        Path target = ("private".equals(bucket) ? root.resolve(".private") : root).resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("文件路径越界");
        return target;
    }

    @Override public void upload(PlatformServiceRuntimeDTO config, String bucket, String key,
                                 InputStream stream, long size, String mimeType) throws Exception {
        Path target = path(config, bucket, key);
        Files.createDirectories(target.getParent());
        Files.copy(stream, target);
    }
    @Override public void delete(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        Path target = path(config, bucket, key);
        if (Files.exists(target) && !target.toRealPath().startsWith(Path.of(config.getUploadPath()).toRealPath()))
            throw new IllegalArgumentException("文件路径越界");
        Files.deleteIfExists(target);
    }
    @Override public boolean exists(PlatformServiceRuntimeDTO config, String bucket, String key) {
        return Files.isRegularFile(path(config, bucket, key));
    }
    @Override public StorageMetadata metadata(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        Path target = path(config, bucket, key);
        return new StorageMetadata(Files.size(target), Files.probeContentType(target));
    }
    @Override public String publicUrl(PlatformServiceRuntimeDTO config, String key) {
        return config.getBaseUrl().replaceAll("/+$", "") + "/" + key;
    }
    @Override public String signedUrl(PlatformServiceRuntimeDTO config, String bucket, String key, Duration ttl) {
        return null; // 本地私有文件通过鉴权代理下载。
    }
    @Override public InputStream open(PlatformServiceRuntimeDTO config, String bucket, String key) throws Exception {
        Path target = path(config, bucket, key);
        if (!Files.isRegularFile(target) || !target.toRealPath().startsWith(Path.of(config.getUploadPath()).toRealPath()))
            throw new IllegalArgumentException("文件不存在或路径越界");
        return Files.newInputStream(target);
    }
    @Override public void copy(PlatformServiceRuntimeDTO config, String sourceBucket, String targetBucket, String key) throws Exception {
        Path target = path(config, targetBucket, key);
        Files.createDirectories(target.getParent());
        Files.copy(path(config, sourceBucket, key), target);
    }
    @Override public void checkConnection(PlatformServiceRuntimeDTO config, String bucket) throws Exception {
        Path root = Path.of(config.getUploadPath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (!Files.isWritable(root)) throw new IllegalStateException("本地上传目录不可写");
    }
}
