package org.hongtu.config.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.hongtu.api.dto.PlatformServiceConfigDTO;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.config.DAO.PlatformServiceConfigMapper;
import org.hongtu.config.DTO.PlatformServiceConfigRecord;
import org.hongtu.config.DTO.FileStorageProfile;
import org.hongtu.exception.BusinessException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Set;

/** 超级管理员配置平台消息与文件通道，运行读取直达数据库以便保存后立即生效。 */
@Service
public class PlatformServiceConfigService {
    private static final int MESSAGE_ID = 2;
    private static final int FILE_ID = 3;
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> MESSAGE_FIELDS = Set.of("revision", "mailHost", "mailPort", "mailUsername",
            "mailFrom", "mailProtocol", "encryptedMailPassword", "smsAppKey", "smsTemplateId", "smsHost",
            "encryptedSmsAppSecret", "encryptedSmsAppcode");
    private static final Set<String> FILE_FIELDS = Set.of("revision", "ossEnabled", "uploadPath", "baseUrl",
            "allowedTypes", "maxSize", "encryptedQiniuAccessKey", "encryptedQiniuSecretKey", "qiniuBucket", "qiniuDomain",
            "qiniuPrivateBucket", "qiniuPrivateDomain",
            "storageMode", "profileId", "minioEndpoint", "minioBrowserEndpoint", "minioPublicBucket",
            "minioPrivateBucket", "minioPublicBaseUrl", "encryptedMinioAccessKey", "encryptedMinioSecretKey",
            "ossEndpoint", "ossPublicBucket", "ossPrivateBucket", "ossPublicBaseUrl",
            "encryptedOssAccessKey", "encryptedOssSecretKey");
    private final PlatformServiceConfigMapper mapper;
    private final AiModelKeyCipher cipher;
    private final WebsiteConfigService websiteConfigService;

    public PlatformServiceConfigService(PlatformServiceConfigMapper mapper, AiModelKeyCipher cipher,
                                        WebsiteConfigService websiteConfigService) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.websiteConfigService = websiteConfigService;
    }

    /** 只回显非敏感字段与密钥配置状态。 */
    public PlatformServiceConfigDTO getMessageForEdit() { return getForEdit(MESSAGE_ID); }

    public PlatformServiceConfigDTO getFileForEdit() { return getForEdit(FILE_ID); }

    private PlatformServiceConfigDTO getForEdit(int id) {
        PlatformServiceConfigRecord record = read(id);
        PlatformServiceConfigDTO view = new PlatformServiceConfigDTO();
        BeanUtils.copyProperties(record, view);
        view.setMailPasswordConfigured(hasText(record.getEncryptedMailPassword()));
        view.setSmsAppSecretConfigured(hasText(record.getEncryptedSmsAppSecret()));
        view.setSmsAppcodeConfigured(hasText(record.getEncryptedSmsAppcode()));
        view.setQiniuAccessKeyConfigured(hasText(record.getEncryptedQiniuAccessKey()));
        view.setQiniuSecretKeyConfigured(hasText(record.getEncryptedQiniuSecretKey()));
        view.setMinioAccessKeyConfigured(hasText(record.getEncryptedMinioAccessKey()));
        view.setMinioSecretKeyConfigured(hasText(record.getEncryptedMinioSecretKey()));
        view.setOssAccessKeyConfigured(hasText(record.getEncryptedOssAccessKey()));
        view.setOssSecretKeyConfigured(hasText(record.getEncryptedOssSecretKey()));
        view.setStorageMode(resolveMode(record));
        return view;
    }

    /** 乐观锁发布；空密钥沿用旧值，显式清除才删除。 */
    @Transactional(rollbackFor = Exception.class)
    public PlatformServiceConfigDTO saveMessage(PlatformServiceConfigDTO submitted, long userId) {
        return save(submitted, userId, MESSAGE_ID);
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformServiceConfigDTO saveFile(PlatformServiceConfigDTO submitted, long userId) {
        return save(submitted, userId, FILE_ID);
    }

    private PlatformServiceConfigDTO save(PlatformServiceConfigDTO submitted, long userId, int id) {
        if (submitted == null || submitted.getRevision() == null) throw new BusinessException(400, "配置版本不能为空");
        PlatformServiceConfigRecord record = read(id);
        long expected = submitted.getRevision();
        if (!record.getRevision().equals(expected)) throw new BusinessException(409, "配置已被其他管理员更新，请重新加载");
        BeanUtils.copyProperties(submitted, record, "revision", "websiteName", "mailPassword", "smsAppSecret",
                "smsAppcode", "qiniuAccessKey", "qiniuSecretKey", "minioAccessKey", "minioSecretKey",
                "ossAccessKey", "ossSecretKey", "profileId");
        if (id == MESSAGE_ID) {
            record.setEncryptedMailPassword(merge(record.getEncryptedMailPassword(), submitted.getMailPassword(), submitted.getClearMailPassword()));
            record.setEncryptedSmsAppSecret(merge(record.getEncryptedSmsAppSecret(), submitted.getSmsAppSecret(), submitted.getClearSmsAppSecret()));
            record.setEncryptedSmsAppcode(merge(record.getEncryptedSmsAppcode(), submitted.getSmsAppcode(), submitted.getClearSmsAppcode()));
            validateMessage(record);
        } else {
            record.setEncryptedQiniuAccessKey(merge(record.getEncryptedQiniuAccessKey(), submitted.getQiniuAccessKey(), submitted.getClearQiniuAccessKey()));
            record.setEncryptedQiniuSecretKey(merge(record.getEncryptedQiniuSecretKey(), submitted.getQiniuSecretKey(), submitted.getClearQiniuSecretKey()));
            record.setEncryptedMinioAccessKey(merge(record.getEncryptedMinioAccessKey(), submitted.getMinioAccessKey(), submitted.getClearMinioAccessKey()));
            record.setEncryptedMinioSecretKey(merge(record.getEncryptedMinioSecretKey(), submitted.getMinioSecretKey(), submitted.getClearMinioSecretKey()));
            record.setEncryptedOssAccessKey(merge(record.getEncryptedOssAccessKey(), submitted.getOssAccessKey(), submitted.getClearOssAccessKey()));
            record.setEncryptedOssSecretKey(merge(record.getEncryptedOssSecretKey(), submitted.getOssSecretKey(), submitted.getClearOssSecretKey()));
            if (record.getAllowedTypes() != null)
                record.setAllowedTypes(java.util.Arrays.stream(record.getAllowedTypes().split(","))
                        .map(value -> value.trim().toLowerCase()).filter(value -> !value.isEmpty())
                        .distinct().collect(java.util.stream.Collectors.joining(",")));
            validateFile(record);
            FileStorageProfile profile = new FileStorageProfile();
            profile.setConfigJson(JSON.toJSONString(record));
            mapper.insertProfile(profile);
            record.setProfileId(profile.getId());
        }
        record.setRevision(expected + 1);
        JSONObject document = (JSONObject) JSON.toJSON(record);
        document.keySet().retainAll(id == MESSAGE_ID ? MESSAGE_FIELDS : FILE_FIELDS);
        if (mapper.publish(id, document.toJSONString(), expected, userId) != 1)
            throw new BusinessException(409, "配置已被其他管理员更新，请重新加载");
        return getForEdit(id);
    }

    /** 消息服务获取最新凭据；调用方仅为经过内部鉴权的服务。 */
    public PlatformServiceRuntimeDTO messageRuntime() {
        PlatformServiceConfigRecord record = read(MESSAGE_ID);
        PlatformServiceRuntimeDTO runtime = runtimeBase(record);
        runtime.setWebsiteName(websiteConfigService.getPublished().getSystemName());
        runtime.setMailPassword(cipher.decrypt(record.getEncryptedMailPassword()));
        runtime.setSmsAppSecret(cipher.decrypt(record.getEncryptedSmsAppSecret()));
        runtime.setSmsAppcode(cipher.decrypt(record.getEncryptedSmsAppcode()));
        return runtime;
    }

    /** 文件服务获取最新存储参数。 */
    public PlatformServiceRuntimeDTO fileRuntime() {
        return fileRuntime(null);
    }

    /** 历史文件绑定旧配置快照；未绑定时读取当前配置。 */
    public PlatformServiceRuntimeDTO fileRuntime(Long profileId) {
        String json = profileId == null ? null : mapper.readProfile(profileId);
        if (profileId != null && json == null) throw new BusinessException(404, "文件存储配置快照不存在");
        PlatformServiceConfigRecord record = json == null ? read(FILE_ID) : JSON.parseObject(json, PlatformServiceConfigRecord.class);
        PlatformServiceRuntimeDTO runtime = runtimeBase(record);
        runtime.setStorageMode(resolveMode(record));
        runtime.setQiniuAccessKey(cipher.decrypt(record.getEncryptedQiniuAccessKey()));
        runtime.setQiniuSecretKey(cipher.decrypt(record.getEncryptedQiniuSecretKey()));
        runtime.setMinioAccessKey(cipher.decrypt(record.getEncryptedMinioAccessKey()));
        runtime.setMinioSecretKey(cipher.decrypt(record.getEncryptedMinioSecretKey()));
        runtime.setOssAccessKey(cipher.decrypt(record.getEncryptedOssAccessKey()));
        runtime.setOssSecretKey(cipher.decrypt(record.getEncryptedOssSecretKey()));
        return runtime;
    }

    /** 静态资源请求只读取本地目录，避免传递云存储凭据。 */
    public String currentUploadPath() { return read(FILE_ID).getUploadPath(); }

    private PlatformServiceRuntimeDTO runtimeBase(PlatformServiceConfigRecord record) {
        PlatformServiceRuntimeDTO runtime = new PlatformServiceRuntimeDTO();
        BeanUtils.copyProperties(record, runtime);
        return runtime;
    }

    private PlatformServiceConfigRecord read(int id) {
        String json = mapper.read(id);
        if (json == null) throw new BusinessException(503, "平台服务配置尚未初始化，请执行数据库迁移");
        return JSON.parseObject(json, PlatformServiceConfigRecord.class);
    }

    private String resolveMode(PlatformServiceRuntimeDTO config) {
        return hasText(config.getStorageMode()) ? config.getStorageMode() :
                Boolean.TRUE.equals(config.getOssEnabled()) ? "QINIU" : "LOCAL";
    }

    private String merge(String oldValue, String submitted, Boolean clear) {
        String value = submitted == null ? "" : submitted.trim();
        if (Boolean.TRUE.equals(clear) && !value.isEmpty()) throw new BusinessException(400, "清除与替换密钥不能同时进行");
        if (Boolean.TRUE.equals(clear)) return "";
        return value.isEmpty() ? oldValue : cipher.encrypt(value);
    }

    private void validateMessage(PlatformServiceConfigRecord config) {
        if (config.getMailPort() == null || config.getMailPort() < 1 || config.getMailPort() > 65535)
            throw new BusinessException(400, "SMTP 端口无效");
        if (config.getMailProtocol() == null || !Set.of("smtp", "smtps").contains(config.getMailProtocol()))
            throw new BusinessException(400, "邮件协议仅支持 smtp 或 smtps");
        if (hasText(config.getSmsHost())) requireHttpUrl(config.getSmsHost(), "短信接口地址");
    }

    private void validateFile(PlatformServiceConfigRecord config) {
        if (config.getMaxSize() == null || config.getMaxSize() < 1 || config.getMaxSize() > 2048)
            throw new BusinessException(400, "上传上限须在 1 至 2048 MB 之间");
        if (config.getAllowedTypes() == null || config.getAllowedTypes().isBlank()
                || !IMAGE_TYPES.containsAll(java.util.Arrays.asList(config.getAllowedTypes().toLowerCase().split(","))))
            throw new BusinessException(400, "图片类型只能从 jpg,jpeg,png,gif,webp 中选择");
        String mode = resolveMode(config);
        if (!Set.of("LOCAL", "MINIO", "OSS", "QINIU").contains(mode))
            throw new BusinessException(400, "不支持的存储方式");
        if ("LOCAL".equals(mode) && (!hasText(config.getUploadPath()) || !hasText(config.getBaseUrl())))
            throw new BusinessException(400, "本地上传目录和访问地址不能为空");
        if (hasText(config.getBaseUrl())) requireHttpUrl(config.getBaseUrl(), "文件访问地址");
        if ("MINIO".equals(mode)) {
            requireStorageFields(config.getMinioEndpoint(), config.getMinioPublicBucket(), config.getMinioPrivateBucket(),
                    config.getMinioPublicBaseUrl(), config.getEncryptedMinioAccessKey(), config.getEncryptedMinioSecretKey(), "MinIO");
            requireHttpUrl(config.getMinioEndpoint(), "MinIO API Endpoint");
            if (hasText(config.getMinioBrowserEndpoint())) requireHttpUrl(config.getMinioBrowserEndpoint(), "MinIO 浏览器 API Endpoint");
        }
        if ("OSS".equals(mode)) {
            requireStorageFields(config.getOssEndpoint(), config.getOssPublicBucket(), config.getOssPrivateBucket(),
                    config.getOssPublicBaseUrl(), config.getEncryptedOssAccessKey(), config.getEncryptedOssSecretKey(), "OSS");
            requireHttpUrl(config.getOssEndpoint(), "OSS Endpoint");
        }
        if ("QINIU".equals(mode)) {
            if (!hasText(config.getEncryptedQiniuAccessKey()) || !hasText(config.getEncryptedQiniuSecretKey())
                    || !hasText(config.getQiniuBucket()) || !hasText(config.getQiniuDomain()))
                throw new BusinessException(400, "启用七牛云前请填写空间、域名和访问密钥");
            requireHttpUrl(config.getQiniuDomain(), "七牛云访问域名");
            if (hasText(config.getQiniuPrivateBucket()) || hasText(config.getQiniuPrivateDomain())) {
                if (!hasText(config.getQiniuPrivateBucket()) || !hasText(config.getQiniuPrivateDomain())
                        || config.getQiniuBucket().equals(config.getQiniuPrivateBucket()))
                    throw new BusinessException(400, "七牛私有 Bucket 与访问域名须同时配置，且不可与公开 Bucket 相同");
                requireHttpUrl(config.getQiniuPrivateDomain(), "七牛私有访问域名");
            }
        }
    }

    private void requireStorageFields(String endpoint, String publicBucket, String privateBucket,
                                      String publicUrl, String key, String secret, String label) {
        if (!hasText(endpoint) || !hasText(publicBucket) || !hasText(privateBucket)
                || publicBucket.equals(privateBucket) || !hasText(publicUrl) || !hasText(key) || !hasText(secret))
            throw new BusinessException(400, label + "须配置 API 地址、独立的公开/私有 Bucket、公开域名和访问密钥");
        requireHttpUrl(publicUrl, label + "公开域名");
    }

    private void requireHttpUrl(String value, String label) {
        try {
            URI uri = URI.create(value);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) return;
        } catch (IllegalArgumentException ignored) { }
        throw new BusinessException(400, label + "必须是有效的 HTTP(S) 地址");
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
