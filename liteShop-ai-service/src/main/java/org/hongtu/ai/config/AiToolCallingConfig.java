package org.hongtu.ai.config;

import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 经营追问的工具调用限制。只读工具超过轮次后停止，并让模型说明数据不足。
 */
@Configuration
public class AiToolCallingConfig {
    /** 一次追问最多六次工具调用，允许组合多个细粒度指标，同时限制模型反复取数。 */
    static final int MAX_TOOL_CALLS = 6;

    @Bean
    public ToolCallingAdvisor aiManagerToolCallingAdvisor() {
        return ToolCallingAdvisor.builder()
                .toolCallingManager(ToolCallingManager.builder()
                        .maxCallsPerTool(2)
                        .maxTotalToolCalls(MAX_TOOL_CALLS)
                        .onLimitExceeded(ToolCallLimitBehavior.RETURN_ERROR_RESPONSE)
                        .toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder()
                                .alwaysThrow(false)
                                .build())
                        .build())
                .build();
    }
}
