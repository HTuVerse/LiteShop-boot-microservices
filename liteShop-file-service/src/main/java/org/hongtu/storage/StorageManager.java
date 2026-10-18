package org.hongtu.storage;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.hongtu.DTO.FileUpload;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.config.FileUploadConfig;
import org.hongtu.enums.StorageTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** 根据文件自身的存储类型与配置快照选择驱动，切换默认模式仅影响新上传。 */
@Service
@RequiredArgsConstructor
public class StorageManager {
    private final FileUploadConfig config;
    private final FileTypeSniffer sniffer;
    private final List<StorageService> services;

    public StorageService driver(StorageTypeEnum type) {
        return services.stream().filter(service -> service.type() == type).findFirst()
                .orElseThrow(() -> new IllegalStateException("存储驱动未注册：" + type));
    }

    public StorageTypeEnum activeType(PlatformServiceRuntimeDTO settings) {
        String mode = settings.getStorageMode();
        if (mode == null || mode.isBlank()) return Boolean.TRUE.equals(settings.getOssEnabled())
                ? StorageTypeEnum.QINIU : StorageTypeEnum.LOCAL;
        return StorageTypeEnum.valueOf(mode);
    }

    public PlatformServiceRuntimeDTO currentSettings() { return config.current(); }

    public UploadResult upload(MultipartFile file, boolean imageOnly, String visibility) throws Exception {
        PlatformServiceRuntimeDTO settings = config.current();
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (extension == null) throw new IllegalArgumentException("文件名缺少扩展名");
        extension = extension.toLowerCase(java.util.Locale.ROOT);
        if (file.isEmpty() || file.getSize() > settings.getMaxSize() * 1024L * 1024L)
            throw new IllegalArgumentException("文件为空或超过上传大小限制");
        if (imageOnly ? !config.isAllowedImageType(extension) : !config.isAllowedAttachmentType(extension))
            throw new IllegalArgumentException("不支持的文件类型：" + extension);
        String mime = sniffer.detect(file, extension);
        if (imageOnly && !mime.startsWith("image/")) throw new IllegalArgumentException("仅允许上传图片");
        String access = "PRIVATE".equalsIgnoreCase(visibility) ? "PRIVATE" : "PUBLIC";
        StorageTypeEnum type = activeType(settings);
        if (type == StorageTypeEnum.QINIU && "PRIVATE".equals(access)
                && (settings.getQiniuPrivateBucket() == null || settings.getQiniuPrivateBucket().isBlank()))
            throw new IllegalArgumentException("七牛私有 Bucket 尚未配置");
        String key = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String bucket = bucket(settings, type, access);
        StorageService service = driver(type);
        try (InputStream stream = file.getInputStream()) {
            service.upload(settings, bucket, key, stream, file.getSize(), mime);
        }
        String url = "PUBLIC".equals(access) ? service.publicUrl(settings, key) : null;
        return new UploadResult(type, settings.getProfileId(), bucket, key, access, mime, extension, url);
    }

    public record UploadResult(StorageTypeEnum type, Long profileId, String bucket, String key,
                               String visibility, String mime, String extension, String url) { }

    public void apply(FileUpload record, UploadResult result) {
        record.setStorageType(result.type());
        record.setStorageProfileId(result.profileId());
        record.setBucket(result.bucket());
        record.setStorageKey(result.key());
        record.setVisibility(result.visibility());
        record.setFileExtension(result.extension());
        record.setContentType(result.mime());
        record.setUrl(result.url());
    }

    public PlatformServiceRuntimeDTO settings(FileUpload file) { return config.profile(file.getStorageProfileId()); }
    public StorageService driver(FileUpload file) { return driver(file.getStorageType()); }
    public String key(FileUpload file) {
        if (file.getStorageKey() != null && !file.getStorageKey().isBlank()) return file.getStorageKey();
        // 兼容升级前仅保存 URL 的历史记录。
        String url = file.getUrl();
        if (url == null || !url.matches("(?i)^https?://[^?#]+/[0-9]{8}/[a-f0-9]{32}\\.[a-z0-9]{1,10}$"))
            throw new IllegalArgumentException("历史资源 URL 无法推导存储键");
        int last = url.lastIndexOf('/');
        int previous = url.lastIndexOf('/', last - 1);
        return url.substring(previous + 1);
    }
    public String bucket(FileUpload file, PlatformServiceRuntimeDTO settings) {
        return file.getBucket() != null ? file.getBucket() : bucket(settings, file.getStorageType(), visibility(file));
    }
    public String bucket(PlatformServiceRuntimeDTO settings, StorageTypeEnum type, String visibility) {
        boolean privateFile = "PRIVATE".equals(visibility);
        return switch (type) {
            case LOCAL -> privateFile ? "private" : "public";
            case MINIO -> privateFile ? settings.getMinioPrivateBucket() : settings.getMinioPublicBucket();
            case OSS -> privateFile ? settings.getOssPrivateBucket() : settings.getOssPublicBucket();
            case QINIU -> privateFile ? settings.getQiniuPrivateBucket() : settings.getQiniuBucket();
        };
    }
    public String visibility(FileUpload file) {
        return "PRIVATE".equals(file.getVisibility()) ? "PRIVATE" : "PUBLIC";
    }
    public void delete(FileUpload file) throws Exception {
        PlatformServiceRuntimeDTO settings = settings(file);
        driver(file).delete(settings, bucket(file, settings), key(file));
    }
    public String accessUrl(FileUpload file) throws Exception {
        if ("PUBLIC".equals(visibility(file))) return file.getUrl();
        PlatformServiceRuntimeDTO settings = settings(file);
        return driver(file).signedUrl(settings, bucket(file, settings), key(file), Duration.ofMinutes(5));
    }
    public InputStream open(FileUpload file) throws Exception {
        PlatformServiceRuntimeDTO settings = settings(file);
        return driver(file).open(settings, bucket(file, settings), key(file));
    }
    public String changeVisibility(FileUpload file, String target) throws Exception {
        if (!"PUBLIC".equals(target) && !"PRIVATE".equals(target)) throw new IllegalArgumentException("资源权限不合法");
        if (visibility(file).equals(target)) return file.getUrl();
        PlatformServiceRuntimeDTO settings = settings(file);
        StorageService service = driver(file);
        String key = key(file);
        String from = bucket(file, settings), to = bucket(settings, file.getStorageType(), target);
        if (to == null || to.isBlank()) throw new IllegalStateException("目标私有或公开 Bucket 尚未配置");
        if (from.equals(to)) throw new IllegalStateException("公开和私有文件必须使用隔离的存储位置");
        service.copy(settings, from, to, key);
        file.setStorageKey(key);
        file.setBucket(to);
        file.setVisibility(target);
        file.setUrl("PUBLIC".equals(target) ? service.publicUrl(settings, key) : null);
        return from;
    }
}
