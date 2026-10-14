package org.hongtu.api.dto;
import lombok.Data;
import lombok.ToString;
/** 仅通过鉴权内部接口提供给 AI 服务的运行配置，禁止返回浏览器。 */
@Data
public class AiModelRuntimeConfigDTO {
    private Long revision;
    private Boolean enabled;
    private String baseUrl;
    private String model;
    @ToString.Exclude private String apiKey;
}
