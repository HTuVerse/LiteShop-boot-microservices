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
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.LongConsumer;

/** 根据文件自身的存储类型与配置快照选择驱动，切换默认模式仅影响新上传。 */
@Service
@RequiredArgsConstructor
public class StorageManager {
    private final FileUploadConfig config;
    private final FileTypeSniffer sniffer;
    private final ImageCompressor imageCompressor;
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

    public UploadResult upload(MultipartFile file, boolean imageOnly, String visibility,
                               LongConsumer beforeStore) throws Exception {
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
        byte[] compressed = mime.startsWith("image/") ? imageCompressor.compress(file, extension, settings) : null;
        long storedSize = compressed == null ? file.getSize() : compressed.length;
        if (beforeStore != null) beforeStore.accept(storedSize);
        String access = "PRIVATE".equalsIgnoreCase(visibility) ? "PRIVATE" : "PUBLIC";
        StorageTypeEnum type = activeType(settings);
        if (type == StorageTypeEnum.QINIU && "PRIVATE".equals(access)
                && (settings.getQiniuPrivateBucket() == null || settings.getQiniuPrivateBucket().isBlank()))
            throw new IllegalArgumentException("七牛私有 Bucket 尚未配置");
        String key = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String bucket = bucket(settings, type, access);
        StorageService service = driver(type);
        try (InputStream stream = compressed == null ? file.getInputStream() : new ByteArrayInputStream(compressed)) {
            service.upload(settings, bucket, key, stream, storedSize, mime);
        }
        return new UploadResult(type, settings.getProfileId(), bucket, key, access, mime, extension, storedSize);
    }

    public record UploadResult(StorageTypeEnum type, Long profileId, String bucket, String key,
                               String visibility, String mime, String extension, long size) { }

    public void apply(FileUpload record, UploadResult result) {
        record.setStorageType(result.type());
        record.setStorageProfileId(result.profileId());
        record.setBucket(result.bucket());
        record.setStorageKey(result.key());
        record.setVisibility(result.visibility());
        record.setFileExtension(result.extension());
        record.setContentType(result.mime());
        // URL 是访问结果，不再作为新文件的持久化定位数据。
        record.setUrl(null);
    }

    public PlatformServiceRuntimeDTO settings(FileUpload file) { return config.profile(file.getStorageProfileId()); }
    public StorageService driver(FileUpload file) {
        return driver(file.getStorageType() == null ? activeType(settings(file)) : file.getStorageType());
    }
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
        StorageTypeEnum type = file.getStorageType() == null ? activeType(settings) : file.getStorageType();
        return file.getBucket() != null ? file.getBucket() : bucket(settings, type, visibility(file));
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
    /** 公开地址由统一入口和资源 ID 派生；未启用入口时由文件绑定的历史驱动配置生成。 */
    public String publicUrl(FileUpload file) {
        return publicUrl(file, currentSettings().getPublicDeliveryBaseUrl());
    }
    public String publicUrl(FileUpload file, String deliveryBase) {
        if (!"PUBLIC".equals(visibility(file))) return null;
        if (deliveryBase != null && !deliveryBase.isBlank() && file.getId() != null)
            return deliveryBase.replaceAll("/+$", "") + "/" + file.getId();
        return originPublicUrl(file);
    }
    /** 公开入口重定向到真实源站；读取文件绑定的快照，默认驱动切换不会改变目标。 */
    public String originPublicUrl(FileUpload file) {
        if (!"PUBLIC".equals(visibility(file))) return null;
        try {
            return driver(file).publicUrl(settings(file), key(file));
        } catch (IllegalArgumentException | IllegalStateException error) {
            // 升级前无法识别 Key 的外部文件仅保留其原访问地址。
            return file.getUrl();
        }
    }
    /** API 响应兼容旧 url 字段，但不会将派生地址写回数据库。 */
    public FileUpload forResponse(FileUpload file) {
        return forResponse(file, currentSettings().getPublicDeliveryBaseUrl());
    }
    public FileUpload forResponse(FileUpload file, String deliveryBase) {
        file.setUrl(publicUrl(file, deliveryBase));
        return file;
    }
    public void delete(FileUpload file) throws Exception {
        PlatformServiceRuntimeDTO settings = settings(file);
        driver(file).delete(settings, bucket(file, settings), key(file));
    }
    public String accessUrl(FileUpload file) throws Exception {
        if ("PUBLIC".equals(visibility(file))) return publicUrl(file);
        PlatformServiceRuntimeDTO settings = settings(file);
        return driver(file).signedUrl(settings, bucket(file, settings), key(file), Duration.ofMinutes(5));
    }
    public InputStream open(FileUpload file) throws Exception {
        PlatformServiceRuntimeDTO settings = settings(file);
        return driver(file).open(settings, bucket(file, settings), key(file));
    }
    public String changeVisibility(FileUpload file, String target) throws Exception {
        if (!"PUBLIC".equals(target) && !"PRIVATE".equals(target)) throw new IllegalArgumentException("资源权限不合法");
        if (visibility(file).equals(target)) return publicUrl(file);
        PlatformServiceRuntimeDTO settings = settings(file);
        StorageService service = driver(file);
        String key = key(file);
        StorageTypeEnum type = file.getStorageType() == null ? activeType(settings) : file.getStorageType();
        String from = bucket(file, settings), to = bucket(settings, type, target);
        if (to == null || to.isBlank()) throw new IllegalStateException("目标私有或公开 Bucket 尚未配置");
        if (from.equals(to)) throw new IllegalStateException("公开和私有文件必须使用隔离的存储位置");
        service.copy(settings, from, to, key);
        file.setStorageKey(key);
        file.setBucket(to);
        file.setVisibility(target);
        file.setUrl(null);
        return from;
    }
}
