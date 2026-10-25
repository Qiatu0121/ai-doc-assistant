package com.example.aidoc.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 运行参数。
 */
@Data
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

    /** 单次请求允许执行的工具次数上限 */
    private int maxToolCalls = 6;

    /** 知识库检索默认条数 */
    private int defaultTopK = 4;

    /** 向量检索相似度阈值 */
    private double similarityThreshold = 0.3;
}
