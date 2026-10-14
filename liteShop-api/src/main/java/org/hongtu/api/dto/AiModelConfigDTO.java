package org.hongtu.api.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;
/** 平台模型配置表单，已有密钥不回显，留空时保留。 */
@Data
public class AiModelConfigDTO {
    @NotNull @Min(0) private Long revision = 0L;
    @NotNull private Boolean enabled = false;
    @NotNull @Size(max = 500) private String baseUrl = "https://api.openai.com/v1";
    @NotNull @Size(max = 200) private String model = "";
    @Size(max = 4096) @ToString.Exclude private String apiKey;
    /** 仅表示已保存密钥，不包含密钥内容。 */
    private boolean keyConfigured;
    @NotNull private Boolean clearApiKey = false;
}
