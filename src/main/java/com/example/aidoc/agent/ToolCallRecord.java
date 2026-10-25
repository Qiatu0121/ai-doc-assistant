package com.example.aidoc.agent;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 单次工具调用记录，同时写入服务端日志并返回给前端用于展示。
 */
@Data
@AllArgsConstructor
public class ToolCallRecord {

    private int callIndex;

    private String toolName;

    private String arguments;

    private boolean success;

    private long durationMs;

    private String resultPreview;

    private String error;
}
