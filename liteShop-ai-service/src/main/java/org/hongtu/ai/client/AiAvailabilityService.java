package org.hongtu.ai.client;

import cn.dev33.satoken.stp.StpUtil;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import org.hongtu.ai.model.BriefingResult;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.hongtu.api.dto.AiModelConnectionTestDTO;
import org.hongtu.api.dto.AiModelRuntimeConfigDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/** AI 模型可用性检查和按角色展示的故障提示，不暴露供应商响应或密钥。 */
@Service
public class AiAvailabilityService {
    private final AiRuntimeModelService models;

    public AiAvailabilityService(AiRuntimeModelService models) {
        this.models = models;
    }

    /** 状态检查服务器生效配置，供应商额度是否耗尽以实际调用结果为准。 */
    public Status status() {
        try {
            return models.currentModel() == null ? unavailable("NOT_CONFIGURED")
                    : new Status(true, "READY", "", canConfigure());
        } catch (RuntimeException error) {
            return unavailable("CONFIG_UNAVAILABLE");
        }
    }

    /** 调用业务数据接口前检查模型，避免未配置时产生伪分析。 */
    public ChatModel requireModel() {
        ChatModel model;
        try {
            model = models.currentModel();
        } catch (RuntimeException error) {
            throw failure("CONFIG_UNAVAILABLE");
        }
        if (model == null) throw failure("NOT_CONFIGURED");
        return model;
    }

    /** 区分凭据错误、额度耗尽、限流和其他模型故障。 */
    public BusinessException modelFailure(RuntimeException error) {
        return failure(classifyModelFailure(error));
    }

    /** 使用配置页传入的模型参数发起独立结构化请求，不进入 AI 店长业务。 */
    public AiModelConnectionTestDTO testConnection(AiModelRuntimeConfigDTO config) {
        ChatModel model = models.testModel(config);
        if (model == null) throw new BusinessException("模型接口测试配置不完整或接口地址不可用");

        long startedAt = System.nanoTime();
        try {
            // 经营洞察依赖结构化结果，不能只用普通文本 OK 判断模型可用。
            var response = ChatClient.create(model).prompt()
                    .user("请返回一条最小经营诊断；keyPoints、evidence、todayTasks 各包含一项，所有文本字段保持简短。"
                            + AiStructuredOutputParser.contract(BriefingResult.class))
                    .options(ChatOptions.builder().maxTokens(512))
                    .call().chatResponse();
            BriefingResult result = AiStructuredOutputParser.parse(response, BriefingResult.class);
            if (result.keyPoints() == null || result.keyPoints().isEmpty()
                    || result.evidence() == null || result.evidence().isEmpty()
                    || result.todayTasks() == null || result.todayTasks().isEmpty()) {
                throw new InvalidModelResponseException("模型接口未返回有效的结构化响应");
            }
            long elapsedMillis = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
            return new AiModelConnectionTestDTO(true, elapsedMillis);
        } catch (RuntimeException error) {
            throw connectionTestFailure(error);
        }
    }

    private BusinessException connectionTestFailure(RuntimeException error) {
        String reason = classifyModelFailure(error);
        int code = switch (reason) {
            case "RATE_LIMITED" -> 429;
            case "NETWORK_UNAVAILABLE", "PROVIDER_UNAVAILABLE", "MODEL_UNAVAILABLE" -> 502;
            default -> 400;
        };
        String message = switch (reason) {
            case "INVALID_KEY" -> "模型接口连接失败：API Key 无效或没有访问权限";
            case "QUOTA_EXHAUSTED" -> "模型接口连接失败：供应商额度或余额不足";
            case "RATE_LIMITED" -> "模型接口连接失败：供应商请求频率受限，请稍后重试";
            case "NETWORK_UNAVAILABLE" -> "模型接口连接失败：服务器无法访问该地址，请检查网络、DNS、代理或证书";
            case "PROVIDER_UNAVAILABLE" -> "模型接口连接失败：模型供应商服务暂时不可用";
            case "INVALID_MODEL_CONFIG" -> "模型接口连接失败：接口地址、模型名称或请求参数不正确";
            case "INVALID_MODEL_RESPONSE" -> "模型接口连接成功，但无法生成系统要求的结构化 JSON，请更换支持指令遵循的对话模型";
            default -> "模型接口连接失败，请检查接口地址、模型名称和 API Key";
        };
        return new BusinessException(code, message);
    }

    /** 只返回可安全记录的故障分类，不包含供应商响应正文或凭据。 */
    public String classifyModelFailure(RuntimeException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String detail = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (cause instanceof RestClientResponseException response)
                detail += response.getResponseBodyAsString().toLowerCase(Locale.ROOT);
            if (cause instanceof OpenAIServiceException response)
                detail += " " + response.code().orElse("") + " " + response.type().orElse("")
                        + " " + response.body();
            if (detail.contains("insufficient_quota") || detail.contains("quota_exceeded")
                    || detail.contains("insufficient_balance") || detail.contains("balance is insufficient")
                    || detail.contains("credit balance") || detail.contains("insufficient_credits")
                    || detail.contains("billing_hard_limit_reached") || detail.contains("exceeded your current quota")
                    || detail.contains("额度") || detail.contains("余额不足")) {
                return "QUOTA_EXHAUSTED";
            }
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAIServiceException response) {
                int status = response.statusCode();
                if (status == 401 || status == 403) return "INVALID_KEY";
                if (status == 402) return "QUOTA_EXHAUSTED";
                if (status == 429) return "RATE_LIMITED";
                if (status == 400 || status == 404 || status == 422) return "INVALID_MODEL_CONFIG";
                if (status == 408 || status >= 500) return "PROVIDER_UNAVAILABLE";
            }
            if (cause instanceof RestClientResponseException response) {
                HttpStatusCode status = response.getStatusCode();
                if (status.value() == 401 || status.value() == 403) return "INVALID_KEY";
                if (status.value() == 402) return "QUOTA_EXHAUSTED";
                if (status.value() == 429) return "RATE_LIMITED";
                if (status.value() == 400 || status.value() == 404 || status.value() == 422)
                    return "INVALID_MODEL_CONFIG";
                if (status.value() == 408 || status.is5xxServerError()) return "PROVIDER_UNAVAILABLE";
            }
            // Spring AI 重试异常可能只保留供应商状态码和错误码。
            String detail = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (detail.contains("invalid_api_key") || detail.contains("incorrect api key")
                    || detail.matches("(?s).*\\b(401|403)\\b.*")) return "INVALID_KEY";
            if (detail.matches("(?s).*\\b402\\b.*")) return "QUOTA_EXHAUSTED";
            if (detail.matches("(?s).*\\b429\\b.*") || detail.contains("rate_limit")) return "RATE_LIMITED";
            if (cause instanceof OpenAIIoException) return "NETWORK_UNAVAILABLE";
            if (cause instanceof OpenAIRetryableException) return "PROVIDER_UNAVAILABLE";
            if (cause instanceof ResourceAccessException || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException || cause instanceof UnknownHostException
                    || cause instanceof SSLException || isNetworkFailure(detail)) return "NETWORK_UNAVAILABLE";
            if (isInvalidModelResponse(cause, detail)) return "INVALID_MODEL_RESPONSE";
        }
        return "MODEL_UNAVAILABLE";
    }

    /** Spring AI 的结构化输出转换异常不应被误报为 Key 或接口地址错误。 */
    private boolean isInvalidModelResponse(Throwable cause, String detail) {
        if (cause instanceof InvalidModelResponseException) return true;
        String type = cause.getClass().getName().toLowerCase(Locale.ROOT);
        return type.contains("structuredoutput") || type.contains("outputconverter")
                || type.contains("jsonprocessingexception") || type.contains("jsonparseexception")
                || type.contains("mismatchedinputexception") || type.contains("invalidformatexception")
                // 兼容 Jackson 3（tools.jackson）的解析/绑定异常命名，避免被误判为模型不可用。
                || type.contains("streamreadexception") || type.contains("databindexception")
                || type.contains("valueinstantiationexception") || type.contains("jacksonexception")
                || detail.contains("failed to convert") || detail.contains("could not convert")
                || detail.contains("failed to parse") || detail.contains("could not parse")
                || detail.contains("invalid json") || detail.contains("unexpected character")
                || detail.contains("unexpected end-of-input");
    }

    private boolean isNetworkFailure(String detail) {
        return detail.contains("timeout") || detail.contains("timed out")
                || detail.contains("connection refused") || detail.contains("connect failed")
                || detail.contains("connection error") || detail.contains("connection reset")
                || detail.contains("unexpected end of stream") || detail.contains("stream was reset")
                || detail.contains("broken pipe") || detail.contains("connection abort")
                || detail.contains("unknown host") || detail.contains("no route to host")
                || detail.contains("network is unreachable") || detail.contains("ssl handshake")
                || detail.contains("unable to tunnel through proxy") || detail.contains("proxy connect");
    }

    private BusinessException failure(String reason) {
        // 503 仅用于需要管理员处理的持久故障；瞬时厂商/网络故障不能让前端把模型永久关闭。
        int code = switch (reason) {
            case "RATE_LIMITED" -> 429;
            case "NETWORK_UNAVAILABLE", "PROVIDER_UNAVAILABLE", "MODEL_UNAVAILABLE" -> 502;
            default -> 503;
        };
        return new BusinessException(code, unavailable(reason).message());
    }

    private Status unavailable(String reason) {
        boolean admin = canConfigure();
        String message = "AI 店长暂不可用，请稍后重试或联系平台管理员。";
        if (admin) {
            message = switch (reason) {
                case "NOT_CONFIGURED" -> "AI 店长暂不可用，请到网站配置的 AI 模型配置中启用模型并填写接口地址和 API Key。";
                case "INVALID_KEY" -> "AI 店长暂不可用，API Key 无效或无访问权限，请到网站配置的 AI 模型配置中检查相关 Key 和模型配置。";
                case "QUOTA_EXHAUSTED" -> "AI 店长暂不可用，模型 Token 额度或余额已用完，请补充额度，或到网站配置的 AI 模型配置中更换可用的 API Key。";
                case "RATE_LIMITED" -> "AI 店长暂不可用，模型请求已被限流，请稍后重试并检查供应商的调用限制。";
                case "NETWORK_UNAVAILABLE" -> "AI 服务无法连接模型厂商，请检查服务器外网、DNS、HTTPS 代理或证书配置，稍后重试。";
                case "PROVIDER_UNAVAILABLE" -> "AI 模型厂商服务暂时不可用，系统已自动重试，请稍后再次分析。";
                case "INVALID_MODEL_CONFIG" -> "AI 店长暂不可用，模型厂商不接受当前接口地址、模型名称或请求参数，请检查网站配置中的 AI 模型配置。";
                case "INVALID_MODEL_RESPONSE" -> "AI 模型已连接，但返回内容不符合经营洞察所需的结构化格式，请重新分析或更换指令遵循能力更强的对话模型。";
                case "CONFIG_UNAVAILABLE" -> "AI 店长暂不可用，服务器模型配置读取失败，请检查配置服务并在网站配置的 AI 模型配置中确认配置。";
                default -> "AI 店长暂不可用，请检查网站配置的 AI 模型配置中的模型、接口地址和 API Key，或稍后重试。";
            };
        }
        return new Status(false, admin ? reason : "UNAVAILABLE", message, admin);
    }

    private boolean canConfigure() {
        // 模型凭据为平台级配置；租户基础配置权限不代表能管理模型密钥。
        if (TenantContext.isSuperAdmin()) return true;
        try { return StpUtil.hasRole("SUPER_ADMIN"); }
        catch (RuntimeException ignored) { return false; }
    }

    /** 对外状态不包含模型密钥或供应商异常正文。 */
    public record Status(boolean enabled, String reason, String message, boolean canConfigure) {}

    /** 连通性测试返回了响应，但没有满足最小结构化输出契约。 */
    private static final class InvalidModelResponseException extends RuntimeException {
        private InvalidModelResponseException(String message) {
            super(message);
        }
    }

}
