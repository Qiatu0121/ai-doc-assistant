package com.example.aidoc.agent.dto;

import com.example.aidoc.agent.ToolCallRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Agent 响应：最终回答、工具调用记录以及后端限制值。
 */
@Data
@AllArgsConstructor
public class AgentChatResponse {

    private String requestId;

    private String answer;

    private List<ToolCallRecord> toolCalls;

    private int toolCallCount;

    private int maxToolCalls;

    /** 是否因为达到后端工具调用上限而中止 */
    private boolean limitExceeded;
}
