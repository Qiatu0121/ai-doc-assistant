package com.example.aidoc.agent;

/**
 * 工具调用超过后端限制。该异常会中断 Agent 循环，而不是返回给模型继续尝试。
 */
public class ToolCallLimitExceededException extends RuntimeException {

    public ToolCallLimitExceededException(String message) {
        super(message);
    }
}
