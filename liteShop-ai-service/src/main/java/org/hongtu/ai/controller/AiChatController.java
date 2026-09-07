package org.hongtu.ai.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiChatController {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final String systemPrompt;

    public AiChatController(ObjectProvider<ChatModel> chatModelProvider,
                            @Value("${lite-shop.ai.system-prompt}") String systemPrompt) {
        this.chatModelProvider = chatModelProvider;
        this.systemPrompt = systemPrompt;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "enabled", chatModelProvider.getIfAvailable() != null,
                "provider", "OpenAI-compatible"
        );
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String content = chatClient().prompt()
                .system(systemPrompt)
                .user(request.message())
                .call()
                .content();
        return new ChatResponse(content == null ? "" : content);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@Valid @RequestBody ChatRequest request) {
        return chatClient().prompt()
                .system(systemPrompt)
                .user(request.message())
                .stream()
                .content();
    }

    private ChatClient chatClient() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI chat is disabled. Set AI_CHAT_PROVIDER=openai and configure AI_API_KEY."
            );
        }
        return ChatClient.create(chatModel);
    }

    public record ChatRequest(@NotBlank(message = "message 不能为空") String message) {
    }

    public record ChatResponse(String content) {
    }
}
