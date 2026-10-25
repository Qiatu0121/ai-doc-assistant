package com.example.aidoc.tool;

import com.example.aidoc.agent.AgentProperties;
import com.example.aidoc.service.VectorStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识库检索工具单元测试，不调用真实 Embedding 服务。
 */
class KnowledgeBaseToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsStructuredSearchResult() throws Exception {
        VectorStoreService vectorStoreService = mock(VectorStoreService.class);
        AgentProperties properties = new AgentProperties();
        properties.setDefaultTopK(4);
        properties.setSimilarityThreshold(0.3);

        Document document = new Document("Spring AI 使用 TikaDocumentReader 读取文档", Map.of(
                "documentId", 1L,
                "documentName", "sample",
                "chunkIndex", 0
        ));
        when(vectorStoreService.search("Spring AI", 4, 0.3))
                .thenReturn(List.of(document));

        KnowledgeBaseTool tool = new KnowledgeBaseTool(
                vectorStoreService, properties, objectMapper);

        JsonNode result = objectMapper.readTree(tool.search("Spring AI", null));

        assertTrue(result.get("success").asBoolean());
        assertEquals(1, result.get("count").asInt());
        assertEquals("sample", result.get("results").get(0).get("documentName").asText());
    }

    @Test
    void rejectsBlankQuery() {
        KnowledgeBaseTool tool = new KnowledgeBaseTool(
                mock(VectorStoreService.class), new AgentProperties(), objectMapper);
        assertThrows(IllegalArgumentException.class, () -> tool.search(" ", 4));
    }

    @Test
    void rejectsOutOfRangeTopK() {
        KnowledgeBaseTool tool = new KnowledgeBaseTool(
                mock(VectorStoreService.class), new AgentProperties(), objectMapper);
        assertThrows(IllegalArgumentException.class, () -> tool.search("query", 11));
    }
}
