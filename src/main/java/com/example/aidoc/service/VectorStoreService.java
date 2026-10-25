package com.example.aidoc.service;

import com.example.aidoc.common.BizException;
import com.example.aidoc.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 向量库统一入口。
 * 封装 SimpleVectorStore 的增加、删除和 JSON 持久化，业务层不直接操作向量库。
 */
@Slf4j
@Service
public class VectorStoreService {

    private final SimpleVectorStore vectorStore;
    private final StorageProperties properties;

    public VectorStoreService(SimpleVectorStore vectorStore, StorageProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /** 写入分块向量，并持久化到 JSON 文件。 */
    public synchronized void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        vectorStore.add(documents);
        persist();
    }

    /** 按向量 ID 删除，并持久化。 */
    public synchronized void delete(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        vectorStore.delete(vectorIds);
        persist();
    }

    /** 按语义相似度检索知识库，供 Agent 工具调用。 */
    public synchronized List<Document> search(String query, int topK, double similarityThreshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(request);
    }

    private void persist() {
        try {
            Path file = Paths.get(properties.getVectorStoreFile())
                    .toAbsolutePath()
                    .normalize();
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            vectorStore.save(file.toFile());
            log.info("向量库已保存：{}", file);
        } catch (Exception e) {
            log.error("向量库持久化失败", e);
            throw new BizException("向量库保存失败", e);
        }
    }
}
