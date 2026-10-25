package com.example.aidoc.agent;

import com.example.aidoc.agent.dto.AgentChatResponse;
import com.example.aidoc.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行入口：
 * 创建本次请求上下文 → 调用 ChatClient → 模型自行选择工具 →
 * Spring AI 执行工具并再次调用模型 → 返回最终回答与工具调用记录。
 *
 * <p>工具选择和调用参数由模型决定；后端只负责工具定义、参数校验、
 * 调用次数限制、日志和失败处理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatClient agentChatClient;
    private final AgentProperties properties;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    public AgentChatResponse chat(String message) {
        ensureAiConfigured();
        if (message == null || message.isBlank()) {
            throw new BizException("message 不能为空");
        }
        if (message.length() > 4000) {
            throw new BizException("message 不能超过 4000 字符");
        }

        int maxToolCalls = Math.max(1, properties.getMaxToolCalls());
        AgentCallContext context = new AgentCallContext(
                UUID.randomUUID().toString(), maxToolCalls);
        AgentCallContext.CURRENT.set(context);

        long start = System.currentTimeMillis();
        String answer;
        boolean limitExceeded = false;

        try {
            answer = agentChatClient.prompt()
                    .user(message)
                    .toolContext(Map.of(AgentCallContext.CONTEXT_KEY, context))
                    .call()
                    .content();
        } catch (Exception e) {
            ToolCallLimitExceededException limit = findCause(e, ToolCallLimitExceededException.class);
            if (limit != null) {
                limitExceeded = true;
                answer = "工具调用次数已达到后端上限（" + maxToolCalls
                        + " 次），本次请求已停止继续调用工具。请缩小问题范围后重试。";
                log.warn("Agent 工具调用达到上限 requestId={} max={}",
                        context.getRequestId(), maxToolCalls);
            } else {
                log.error("Agent 调用失败 requestId={}", context.getRequestId(), e);
                throw new BizException("Agent 调用失败：" + errorText(e), e);
            }
        } finally {
            AgentCallContext.CURRENT.remove();
        }

        List<ToolCallRecord> records = context.snapshotRecords();
        if (answer == null || answer.isBlank()) {
            answer = limitExceeded
                    ? "工具调用次数已达到后端上限，模型未返回最终答案。"
                    : "模型没有返回内容";
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Agent 请求完成 requestId={} toolCalls={} maxToolCalls={} limitExceeded={} durationMs={}",
                context.getRequestId(), records.size(), maxToolCalls, limitExceeded, duration);

        return new AgentChatResponse(
                context.getRequestId(),
                answer,
                records,
                records.size(),
                maxToolCalls,
                limitExceeded);
    }

    private void ensureAiConfigured() {
        if (openAiApiKey == null || openAiApiKey.isBlank()
                || "not-configured".equals(openAiApiKey)) {
            throw new BizException("未配置 OPENAI_API_KEY，无法使用 Agent");
        }
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private String errorText(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
    }
}
