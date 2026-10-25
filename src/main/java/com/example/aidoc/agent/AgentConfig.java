package com.example.aidoc.agent;

import com.example.aidoc.tool.ErrorLogTool;
import com.example.aidoc.tool.KnowledgeBaseTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Agent 配置：
 * 从 @Tool 方法生成工具定义，包装成带调用次数限制和日志的工具回调，
 * 再一次性注册到 ChatClient。模型通过 Function Calling 自行选择工具。
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    @Bean
    public ChatClient agentChatClient(ChatClient.Builder chatClientBuilder,
                                      KnowledgeBaseTool knowledgeBaseTool,
                                      ErrorLogTool errorLogTool,
                                      ObjectMapper objectMapper) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(knowledgeBaseTool, errorLogTool)
                .build()
                .getToolCallbacks();

        List<ToolCallback> wrappedCallbacks = Arrays.stream(callbacks)
                .<ToolCallback>map(callback ->
                        new CountingLoggingToolCallback(callback, objectMapper))
                .toList();

        return chatClientBuilder.clone()
                .defaultSystem(AgentPrompts.SYSTEM)
                .defaultToolCallbacks(wrappedCallbacks)
                .build();
    }
}
