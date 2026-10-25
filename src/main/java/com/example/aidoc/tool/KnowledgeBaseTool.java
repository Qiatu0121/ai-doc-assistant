package com.example.aidoc.tool;

import com.example.aidoc.agent.AgentProperties;
import com.example.aidoc.service.VectorStoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具 1：检索已上传的技术文档知识库。
 * 工具只负责执行检索和返回结构化结果；是否调用由模型决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseTool {

    private final VectorStoreService vectorStoreService;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    @Tool(
            name = "searchKnowledgeBase",
            description = "在已上传的技术文档知识库中检索与问题相关的内容。"
                    + "当用户询问技术概念、配置方法、实现步骤，或需要文档依据时使用。"
    )
    public String search(
            @ToolParam(description = "检索问题或关键词，使用具体的技术术语", required = true)
            String query,
            @ToolParam(description = "返回结果数量，1-10，默认 4", required = false)
            Integer topK) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (query.length() > 500) {
            throw new IllegalArgumentException("query 长度不能超过 500");
        }

        int limit = topK == null ? properties.getDefaultTopK() : topK;
        if (limit < 1 || limit > 10) {
            throw new IllegalArgumentException("topK 必须在 1-10 之间");
        }

        List<Document> documents = vectorStoreService.search(
                query, limit, properties.getSimilarityThreshold());

        List<Map<String, Object>> results = new ArrayList<>();
        for (Document document : documents) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", document.getMetadata().get("documentId"));
            item.put("documentName", document.getMetadata().get("documentName"));
            item.put("chunkIndex", document.getMetadata().get("chunkIndex"));
            item.put("score", document.getScore());
            item.put("content", truncate(document.getText(), 600));
            results.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("query", query);
        response.put("count", results.size());
        response.put("results", results);
        if (results.isEmpty()) {
            response.put("message", "知识库中没有检索到相关内容");
        }

        log.info("知识库工具检索完成 query={} count={}", query, results.size());
        return toJson(response);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("知识库结果序列化失败", e);
        }
    }
}
