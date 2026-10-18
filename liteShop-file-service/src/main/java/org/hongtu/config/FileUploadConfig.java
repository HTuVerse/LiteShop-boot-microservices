package org.hongtu.config;

import jakarta.annotation.Resource;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** 文件服务运行配置，从平台配置服务按请求动态读取。 */
@Component
public class FileUploadConfig {
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ATTACHMENT_TYPES = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx",
            "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar", "mp4", "webm");

    @Resource private ConfigFeignClient configClient;
    private volatile PathCache pathCache;

    private record PathCache(String path, long expiresAt) { }

    public PlatformServiceRuntimeDTO current() {
        return FeignResultUtil.unwrap(configClient.platformFile());
    }

    /** 按上传时绑定的配置快照读取历史存储参数。 */
    public PlatformServiceRuntimeDTO profile(Long id) {
        return id == null ? current() : FeignResultUtil.unwrap(configClient.platformFileProfile(id));
    }

    /** 图片读取使用最长 1 秒的目录快照，配置服务短暂不可用时沿用最后有效值。 */
    public String currentUploadPath() {
        PathCache cached = pathCache;
        long now = System.nanoTime();
        if (cached != null && now < cached.expiresAt()) return cached.path();
        synchronized (this) {
            cached = pathCache;
            if (cached != null && now < cached.expiresAt()) return cached.path();
            try {
                String path = FeignResultUtil.unwrap(configClient.platformFilePath());
                if (path == null || path.isBlank()) throw new IllegalStateException("平台上传目录未配置");
                pathCache = new PathCache(path, System.nanoTime() + 1_000_000_000L);
                return path;
            } catch (RuntimeException error) {
                if (cached != null) return cached.path();
                throw error;
            }
        }
    }

    public boolean isOssEnabled() { return Boolean.TRUE.equals(current().getOssEnabled()); }
    public String getUploadPath() { return current().getUploadPath(); }
    public String getBaseUrl() { return current().getBaseUrl(); }
    public int getMaxSize() { return current().getMaxSize(); }

    public List<String> getAllowedTypesList() {
        String types = current().getAllowedTypes();
        return types == null ? List.of() : Arrays.asList(types.toLowerCase().split(","));
    }

    public boolean isAllowedFileType(String extension) {
        return extension != null && getAllowedTypesList().contains(extension.toLowerCase());
    }

    public boolean isAllowedImageType(String extension) {
        return extension != null && IMAGE_TYPES.contains(extension.toLowerCase()) && isAllowedFileType(extension);
    }

    public boolean isAllowedAttachmentType(String extension) {
        return extension != null && ATTACHMENT_TYPES.contains(extension.toLowerCase());
    }
}
