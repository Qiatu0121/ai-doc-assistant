package com.example.aidoc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具回调装饰器：
 * 1. 统计每次请求的工具调用次数，并强制执行后端上限；
 * 2. 记录工具名、参数、耗时、结果摘要和异常；
 * 3. 工具执行异常转成结构化 JSON 返回给模型，让模型自行解释或调整参数。
 *
 * <p>工具是否被调用仍由模型的 tool_calls 决定，这里不做任何关键词路由。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class CountingLoggingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        AgentCallContext context = resolveContext(toolContext);
        String toolName = delegate.getToolDefinition().name();

        int callIndex;
        try {
            callIndex = context.nextCallIndex();
        } catch (ToolCallLimitExceededException e) {
            log.warn("Agent 工具调用达到上限 requestId={} tool={} max={}",
                    context.getRequestId(), toolName, context.getMaxToolCalls());
            throw e;
        }

        long start = System.currentTimeMillis();
        log.info("Agent 工具调用开始 requestId={} call={}/{} tool={} args={}",
                context.getRequestId(), callIndex, context.getMaxToolCalls(),
                toolName, preview(toolInput, 300));

        try {
            String result = delegate.call(toolInput, toolContext);
            long duration = System.currentTimeMillis() - start;
            String resultPreview = preview(result, 500);

            context.addRecord(new ToolCallRecord(
                    callIndex, toolName, preview(toolInput, 300),
                    true, duration, resultPreview, null));

            log.info("Agent 工具调用成功 requestId={} call={} tool={} durationMs={} result={}",
                    context.getRequestId(), callIndex, toolName, duration, resultPreview);
            return result;
        } catch (Exception e) {
            if (e instanceof ToolCallLimitExceededException limitExceeded) {
                throw limitExceeded;
            }

            long duration = System.currentTimeMillis() - start;
            String error = errorText(e);
            context.addRecord(new ToolCallRecord(
                    callIndex, toolName, preview(toolInput, 300),
                    false, duration, null, error));

            log.error("Agent 工具调用失败 requestId={} call={} tool={} durationMs={}",
                    context.getRequestId(), callIndex, toolName, duration, e);
            return errorJson(error);
        }
    }

    private AgentCallContext resolveContext(ToolContext toolContext) {
        if (toolContext != null) {
            Object value = toolContext.getContext().get(AgentCallContext.CONTEXT_KEY);
            if (value instanceof AgentCallContext agentContext) {
                return agentContext;
            }
        }
        AgentCallContext current = AgentCallContext.CURRENT.get();
        if (current != null) {
            return current;
        }
        throw new IllegalStateException("缺少 Agent 工具调用上下文");
    }

    private String errorJson(String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"工具执行失败\"}";
        }
    }

    private String errorText(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
    }

    private String preview(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
