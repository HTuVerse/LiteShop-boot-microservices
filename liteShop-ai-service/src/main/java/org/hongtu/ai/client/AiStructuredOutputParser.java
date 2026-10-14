package org.hongtu.ai.client;

import org.springframework.ai.chat.model.ChatResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 兼容 OpenAI-compatible 模型常见包装文本的结构化输出解析器。 */
public final class AiStructuredOutputParser {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private AiStructuredOutputParser() {
    }

    /** 从模型文本中提取首个完整 JSON 对象并转换为业务结果。 */
    public static <T> T parse(ChatResponse response, Class<T> resultType) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            throw new InvalidStructuredOutputException("模型没有返回可解析内容");
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new InvalidStructuredOutputException("模型返回内容为空");
        }
        String candidate = firstJsonObject(text);
        try {
            return JSON.readValue(candidate, resultType);
        } catch (RuntimeException error) {
            throw new InvalidStructuredOutputException("模型返回的 JSON 与业务结构不匹配", error);
        }
    }

    /** 返回重试时使用的明确 JSON 字段契约，避免供应商忽略通用格式说明。 */
    public static String contract(Class<?> resultType) {
        String example = switch (resultType.getSimpleName()) {
            case "BriefingResult" -> """
                    {"keyPoints":[{"title":"","detail":""}],"evidence":[{"level":"INFO","title":"","detail":"","metricCode":""}],"todayTasks":[{"title":"","description":"","risk":""}]}
                    """;
            case "DishAdviceResult" -> """
                    {"keyPoints":[{"title":"","detail":""}],"hotOpportunities":[{"title":"","detail":""}],"slowMovingRisks":[{"title":"","detail":""}],"insufficientData":[{"title":"","detail":""}],"menuSuggestions":[{"title":"","description":"","risk":""}]}
                    """;
            case "MarketingDraftResult" -> """
                    {"summary":"","audience":"","mechanism":"","budgetConstraint":"","risk":"","reviewMetrics":""}
                    """;
            case "ChatAnswerResult" -> """
                    {"answer":"","keyPoints":[],"evidence":[{"level":"INFO","title":"","detail":"","metricCode":""}],"missingData":"","actions":[],"tip":"","cards":[{"type":"RANKING","title":"","period":"","metricCode":"","items":[{"name":"","value":"","unit":"","percent":0}]}],"followUps":[]}
                    """;
            case "AiDashboardResult" -> """
                    {"headline":"","report":"","score":{"overall":0,"grade":"","comment":"","dimensions":[{"name":"","score":0,"comment":""}]},"cards":[{"id":"","type":"METRIC","level":"INFO","order":1,"size":"MD","title":"","body":"","metricCode":"","value":"","unit":"","changeRate":0,"evidenceRefs":[""],"chart":{"chartType":"LINE","categories":[""],"series":[{"name":"","data":[0]}],"unit":""},"table":{"columns":[{"key":"","label":"","align":"left","numeric":false}],"rows":[[""]]}}]}
                    """;
            default -> "{}";
        };
        return "\n\n输出要求：只返回一个完整 JSON 对象，不要使用 Markdown 代码块，不要添加解释或前后缀。字段结构必须为："
                + example.trim();
    }

    private static String firstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) throw new InvalidStructuredOutputException("模型返回内容中没有 JSON 对象");
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') {
                quoted = true;
                continue;
            }
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return text.substring(start, index + 1);
        }
        throw new InvalidStructuredOutputException("模型返回的 JSON 对象不完整");
    }

    /** 表示模型已响应，但内容无法满足业务结构化结果契约。 */
    public static final class InvalidStructuredOutputException extends RuntimeException {
        public InvalidStructuredOutputException(String message) {
            super(message);
        }

        public InvalidStructuredOutputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
