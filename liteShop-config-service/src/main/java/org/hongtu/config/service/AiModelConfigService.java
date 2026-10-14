package org.hongtu.config.service;
import org.hongtu.api.dto.AiModelConfigDTO;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.hongtu.config.DAO.AiModelConfigMapper;
import org.hongtu.config.DTO.AiModelConfigRecord;
import org.hongtu.exception.BusinessException;
import org.hongtu.utils.AiEndpointGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
/** 平台 AI 配置读写，管理端只读非敏感字段，运行配置由内部接口提供。 */
@Service
public class AiModelConfigService {
    private final AiModelConfigMapper mapper;
    private final AiModelKeyCipher cipher;
    private final AiEndpointGuard endpoints;

    public AiModelConfigService(AiModelConfigMapper mapper, AiModelKeyCipher cipher, AiEndpointGuard endpoints) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.endpoints = endpoints;
    }
    /** 读取表单，已有 Key 仅返回存在状态。 */
    public AiModelConfigDTO getForEdit() { return editView(read()); }

    /** 解析当前表单中的待测配置；空 Key 使用服务器已保存的密钥。 */
    public AiModelRuntimeConfigDTO resolveForTest(AiModelConfigDTO submitted) {
        AiModelConfigRecord current = read();
        if (!current.getRevision().equals(submitted.getRevision())) throw conflict();
        if (Boolean.TRUE.equals(submitted.getClearApiKey()))
            throw new BusinessException("清除 API Key 时无法进行连通性测试");

        String key = submitted.getApiKey() == null ? "" : submitted.getApiKey().trim();
        if (key.isEmpty() && StringUtils.hasText(current.getEncryptedApiKey()))
            key = cipher.decrypt(current.getEncryptedApiKey());
        String model = submitted.getModel() == null ? "" : submitted.getModel().trim();
        String url = endpoints.normalize(submitted.getBaseUrl());
        if (!StringUtils.hasText(key) || !StringUtils.hasText(model) || !StringUtils.hasText(url))
            throw new BusinessException("请先填写接口地址、模型名称和 API Key");

        var result = new AiModelRuntimeConfigDTO();
        result.setRevision(current.getRevision());
        result.setEnabled(true);
        result.setBaseUrl(url);
        result.setModel(model);
        result.setApiKey(key);
        return result;
    }
    /** 保存新版本，空密钥输入不覆盖已有 Key。 */
    @Transactional(rollbackFor = Exception.class)
    public AiModelConfigDTO save(AiModelConfigDTO submitted, long userId) {
        AiModelConfigRecord current = read();
        if (!current.getRevision().equals(submitted.getRevision())) throw conflict();
        String key = submitted.getApiKey() == null ? "" : submitted.getApiKey().trim();
        if (Boolean.TRUE.equals(submitted.getClearApiKey()) && !key.isEmpty()) throw new BusinessException("清除 Key 和替换 Key 不能同时执行");
        String encrypted = Boolean.TRUE.equals(submitted.getClearApiKey()) ? "" : current.getEncryptedApiKey();
        if (!key.isEmpty()) encrypted = cipher.encrypt(key);
        String model = submitted.getModel().trim();
        String url = endpoints.normalize(submitted.getBaseUrl());
        if (Boolean.TRUE.equals(submitted.getEnabled()) && (!StringUtils.hasText(encrypted) || model.isEmpty() || url.isEmpty()))
            throw new BusinessException("启用 AI 店长前请填写接口地址、模型名和 API Key");
        current.setEnabled(submitted.getEnabled()); current.setBaseUrl(url); current.setModel(model); current.setEncryptedApiKey(encrypted);
        if (mapper.save(current, userId) != 1) throw conflict();
        current.setRevision(current.getRevision() + 1);
        return editView(current);
    }
    /** 未首次保存时沿用 YAML，首次保存后关闭状态也以后台配置为准。 */
    public AiModelRuntimeConfigDTO getRuntime() {
        AiModelConfigRecord current = read();
        if (current.getRevision() == 0) return null;
        var result = new AiModelRuntimeConfigDTO();
        result.setRevision(current.getRevision()); result.setEnabled(current.getEnabled()); result.setBaseUrl(current.getBaseUrl()); result.setModel(current.getModel());
        result.setApiKey(Boolean.TRUE.equals(current.getEnabled()) ? cipher.decrypt(current.getEncryptedApiKey()) : "");
        return result;
    }
    private AiModelConfigRecord read() {
        AiModelConfigRecord current = mapper.read();
        if (current == null) throw new BusinessException(503, "AI 模型配置尚未初始化，请执行 AI 配置数据库迁移");
        return current;
    }
    private AiModelConfigDTO editView(AiModelConfigRecord record) {
        var result = new AiModelConfigDTO();
        result.setRevision(record.getRevision()); result.setEnabled(record.getEnabled()); result.setBaseUrl(record.getBaseUrl()); result.setModel(record.getModel());
        result.setKeyConfigured(StringUtils.hasText(record.getEncryptedApiKey()));
        return result;
    }
    private BusinessException conflict() { return new BusinessException(409, "AI 配置已被其他管理员更新，请重新加载后再保存"); }
}
