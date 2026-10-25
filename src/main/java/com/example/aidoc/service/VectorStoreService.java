package com.example.aidoc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量库统一入口。
 * 底层使用 Spring AI 的 ElasticsearchVectorStore，业务层不直接依赖具体实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final VectorStore vectorStore;

    /** 写入分块向量到 Elasticsearch。 */
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        vectorStore.add(documents);
        log.info("向量写入 Elasticsearch，documents={}", documents.size());
    }

    /** 按向量 ID 从 Elasticsearch 删除。 */
    public void delete(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        vectorStore.delete(vectorIds);
        log.info("从 Elasticsearch 删除向量，ids={}", vectorIds.size());
    }

    /** 按语义相似度检索知识库，供 Agent 工具调用。 */
    public List<Document> search(String query, int topK, double similarityThreshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(request);
    }
}
