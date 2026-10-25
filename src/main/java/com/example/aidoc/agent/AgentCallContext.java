package com.example.aidoc.agent;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 Agent 请求的调用上下文：requestId、工具调用计数和调用记录。
 * 通过 ToolContext 传给工具回调；ThreadLocal 作为同步调用的兜底。
 */
@Getter
public class AgentCallContext {

    public static final String CONTEXT_KEY = "agentCallContext";
    public static final ThreadLocal<AgentCallContext> CURRENT = new ThreadLocal<>();

    private final String requestId;
    private final int maxToolCalls;
    private final AtomicInteger callCount = new AtomicInteger();
    private final List<ToolCallRecord> records =
            Collections.synchronizedList(new ArrayList<>());
    private final long startTime = System.currentTimeMillis();

    public AgentCallContext(String requestId, int maxToolCalls) {
        this.requestId = requestId;
        this.maxToolCalls = maxToolCalls;
    }

    /**
     * 获取下一个工具调用序号。超过上限时抛出异常，由 AgentService 统一处理。
     */
    public int nextCallIndex() {
        int next = callCount.incrementAndGet();
        if (next > maxToolCalls) {
            callCount.decrementAndGet();
            throw new ToolCallLimitExceededException(
                    "工具调用次数超过后端上限 " + maxToolCalls);
        }
        return next;
    }

    public int getCallCount() {
        return callCount.get();
    }

    public void addRecord(ToolCallRecord record) {
        records.add(record);
    }

    public List<ToolCallRecord> snapshotRecords() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - startTime;
    }
}
