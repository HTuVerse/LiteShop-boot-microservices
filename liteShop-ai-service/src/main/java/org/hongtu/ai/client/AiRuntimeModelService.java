package org.hongtu.ai.client;

import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.utils.AiEndpointGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.Proxy;
import java.time.Duration;

/** 每次请求读取服务器配置版本，配置变化时替换模型，支持多实例即时生效。 */
@Service
public class AiRuntimeModelService {
    private static final Logger log = LoggerFactory.getLogger(AiRuntimeModelService.class);
    private final ConfigFeignClient configClient;
    private final AiProxyResolver proxyResolver;
    private final AiEndpointGuard endpoints;
    private long revision = -1;
    private ChatModel cachedModel;

    /**
     * AI 服务不扫描公共组件，这里直接使用默认地址解析。
     */
    @Autowired
    public AiRuntimeModelService(ConfigFeignClient configClient, AiProxyResolver proxyResolver) {
        this(configClient, proxyResolver, new AiEndpointGuard());
    }

    public AiRuntimeModelService(ConfigFeignClient configClient, AiProxyResolver proxyResolver, AiEndpointGuard endpoints) {
        this.configClient = configClient;
        this.proxyResolver = proxyResolver;
        this.endpoints = endpoints;
    }

    /** 读取数据库主配置；配置服务故障时不得悄悄复用已被替换或关闭的旧 Key。 */
    public ChatModel currentModel() {
        AiModelRuntimeConfigDTO config = FeignResultUtil.unwrap(configClient.aiModel());
        synchronized (this) {
            if (config == null) return null;
            // 延迟完成的旧版本读取不能把模型退回旧 Key，也不能重新开启已关闭的模型。
            if (config.getRevision() <= revision) return cachedModel;
            ChatModel model = null;
            if (Boolean.TRUE.equals(config.getEnabled()) && StringUtils.hasText(config.getApiKey())
                    && StringUtils.hasText(config.getBaseUrl()) && StringUtils.hasText(config.getModel())
                    && acceptable(config.getBaseUrl())) {
                // MaaS 兼容网关偶尔会返回可重试的 429/5xx 或连接重置。这里的重试发生在
                // 一次业务生成内部，不会重复预占租户次数，也不会重复写生成记录。
                model = createModel(config, Duration.ofSeconds(60), 2);
            }
            cachedModel = model;
            revision = config.getRevision();
            return model;
        }
    }

    /** 为配置页连通性测试创建临时模型，不写入业务模型缓存。 */
    public ChatModel testModel(AiModelRuntimeConfigDTO config) {
        if (config == null || !StringUtils.hasText(config.getApiKey())
                || !StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getModel())
                || !acceptable(config.getBaseUrl())) return null;
        return createModel(config, Duration.ofSeconds(15), 0);
    }

    private ChatModel createModel(AiModelRuntimeConfigDTO config, Duration timeout, int maxRetries) {
        var options = OpenAiChatOptions.builder()
                .apiKey(config.getApiKey()).baseUrl(config.getBaseUrl()).model(config.getModel())
                .timeout(timeout).maxRetries(maxRetries);
        Proxy proxy = proxyResolver.resolve(config.getBaseUrl());
        if (proxy != null) options.proxy(proxy);
        // Spring AI 2.0.1 的 HTTP 构建器没有关闭自动重定向的入口，302 跟随留待后续。
        return OpenAiChatModel.builder().options(options.build()).build();
    }

    /** 已保存的内网或明文地址视为不可用，避免继续把模型服务当作内网探针。 */
    private boolean acceptable(String baseUrl) {
        try {
            endpoints.normalize(baseUrl);
            return true;
        } catch (BusinessException error) {
            log.warn("模型接口地址不可用: {}", error.getMessage());
            return false;
        }
    }
}
