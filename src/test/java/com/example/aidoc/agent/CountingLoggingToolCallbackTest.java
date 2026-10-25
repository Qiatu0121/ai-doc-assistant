package com.example.aidoc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具回调的调用计数与最大次数限制测试。
 */
class CountingLoggingToolCallbackTest {

    @AfterEach
    void clearContext() {
        AgentCallContext.CURRENT.remove();
    }

    @Test
    void stopsAfterMaxToolCalls() {
        AgentCallContext context = new AgentCallContext("test-request", 2);
        AgentCallContext.CURRENT.set(context);

        CountingLoggingToolCallback callback = new CountingLoggingToolCallback(
                fixedCallback("ok"), new ObjectMapper());

        assertEquals("ok", callback.call("{}"));
        assertEquals("ok", callback.call("{}"));
        assertThrows(ToolCallLimitExceededException.class,
                () -> callback.call("{}"));

        assertEquals(2, context.getCallCount());
        assertEquals(2, context.snapshotRecords().size());
        assertTrue(context.snapshotRecords().stream().allMatch(ToolCallRecord::isSuccess));
    }

    @Test
    void convertsToolExceptionToErrorJson() throws Exception {
        AgentCallContext context = new AgentCallContext("test-request", 3);
        AgentCallContext.CURRENT.set(context);

        CountingLoggingToolCallback callback = new CountingLoggingToolCallback(
                fixedCallback(null, new IllegalArgumentException("bad argument")),
                new ObjectMapper());

        String result = callback.call("{}");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("bad argument"));
        assertEquals(1, context.snapshotRecords().size());
        assertTrue(!context.snapshotRecords().get(0).isSuccess());
    }

    private ToolCallback fixedCallback(String result) {
        return fixedCallback(result, null);
    }

    private ToolCallback fixedCallback(String result, RuntimeException error) {
        ToolDefinition definition = ToolDefinition.builder()
                .name("testTool")
                .description("test tool")
                .inputSchema("{}")
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                if (error != null) {
                    throw error;
                }
                return result;
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }
}
