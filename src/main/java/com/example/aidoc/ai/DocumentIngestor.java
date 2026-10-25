package com.example.aidoc.ai;

import com.example.aidoc.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档解析与分块：
 * Tika 抽取纯文本 → TokenTextSplitter 分块 → 为每个分块生成向量 ID 和元数据。
 * 这里只负责生成 Spring AI Document，不直接操作数据库或向量库。
 */
@Slf4j
@Component
public class DocumentIngestor {

    private static final int MAX_CHUNKS = 500;

    public List<Document> parseAndSplit(Path filePath, Long documentId, String documentName) {
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
        List<Document> rawDocuments = reader.read();

        boolean hasText = rawDocuments.stream()
                .anyMatch(document -> document.getText() != null && !document.getText().isBlank());
        if (!hasText) {
            throw new BizException("文档中没有可解析的文本");
        }

        List<Document> pieces = new TokenTextSplitter().apply(rawDocuments);
        List<Document> chunks = new ArrayList<>();
        int index = 0;

        for (Document piece : pieces) {
            String text = piece.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (index >= MAX_CHUNKS) {
                throw new BizException("文档过大，分块数量超过限制 " + MAX_CHUNKS);
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", documentId);
            metadata.put("documentName", documentName);
            metadata.put("chunkIndex", index);

            Document chunk = Document.builder()
                    .id(UUID.randomUUID().toString())
                    .text(text)
                    .metadata(metadata)
                    .build();

            chunks.add(chunk);
            index++;
        }

        if (chunks.isEmpty()) {
            throw new BizException("文档分块结果为空");
        }

        log.info("文档解析完成 documentId={} chunks={}", documentId, chunks.size());
        return chunks;
    }
}
