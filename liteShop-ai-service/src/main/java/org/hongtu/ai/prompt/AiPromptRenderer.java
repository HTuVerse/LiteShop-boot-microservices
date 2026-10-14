package org.hongtu.ai.prompt;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 从 classpath 渲染 AI 店长提示词。用户输入只进入用户模板变量，不进入系统提示。
 */
@Component
public class AiPromptRenderer {
    private final PromptTemplate system;
    private final Map<String, PromptTemplate> tasks;

    public AiPromptRenderer() {
        this.system = template("prompts/ai-manager-system.st");
        this.tasks = Map.of(
                "briefing", template("prompts/briefing-user.st"),
                "dish-advice", template("prompts/dish-advice-user.st"),
                "marketing-draft", template("prompts/marketing-draft-user.st"),
                "chat", template("prompts/chat-user.st"),
                "dashboard", template("prompts/dashboard-user.st")
        );
    }

    /** 渲染不含用户输入的系统提示。 */
    public String system() {
        return system.render(Map.of());
    }

    /** 按任务名渲染用户提示。task 只能是已登记的模板名。 */
    public String user(String task, Map<String, Object> variables) {
        PromptTemplate template = tasks.get(task);
        if (template == null) {
            throw new IllegalArgumentException("未知提示词模板：" + task);
        }
        return template.render(variables);
    }

    private static PromptTemplate template(String path) {
        try {
            String text = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            return new PromptTemplate(text);
        } catch (IOException error) {
            throw new IllegalStateException("AI 提示词模板不存在：" + path, error);
        }
    }
}
