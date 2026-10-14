package org.hongtu.ai;

import org.hongtu.config.JacksonConfig;
import org.hongtu.config.RedisConfig;
import org.hongtu.config.SaTokenConfigure;
import org.hongtu.exception.GlobalExceptionHandler;
import org.hongtu.utils.RedisJobLease;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;

/** AI 店长服务启动类。 */
// 禁止 Spring AI 从 YAML 自动装配文本模型；运行模型只从配置数据库动态创建。
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        OpenAiChatAutoConfiguration.class,
        OpenAiAudioSpeechAutoConfiguration.class,
        OpenAiAudioTranscriptionAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class,
        OpenAiImageAutoConfiguration.class,
        OpenAiModerationAutoConfiguration.class
})
// 只引入 AI 需要的公共能力，不扫描业务数据源、MQ 工具或其他业务组件。
@Import({SaTokenConfigure.class, JacksonConfig.class, RedisConfig.class, GlobalExceptionHandler.class,
        RedisJobLease.class})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "org.hongtu.api.client")
@EnableScheduling
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
