package com.example.aidoc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aidoc.ai.DocumentIngestor;
import com.example.aidoc.common.BizException;
import com.example.aidoc.dto.ChunkVO;
import com.example.aidoc.dto.DocumentVO;
import com.example.aidoc.entity.AiDocument;
import com.example.aidoc.entity.AiDocumentChunk;
import com.example.aidoc.enums.DocumentStatus;
import com.example.aidoc.mapper.AiDocumentChunkMapper;
import com.example.aidoc.mapper.AiDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档业务编排：
 * 保存文件 → 写文档记录 → Tika 解析分块 → 写入向量库 → 保存分块 → 更新状态。
 * 解析和 Embedding 是外部耗时操作，因此不把整个上传方法包在一个长数据库事务里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final FileStorageService fileStorageService;
    private final DocumentIngestor documentIngestor;
    private final VectorStoreService vectorStoreService;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    /** 上传并索引一篇文档。 */
    public DocumentVO upload(MultipartFile file) {
        ensureAiConfigured();
        FileStorageService.StoredFile stored = fileStorageService.save(file);
        LocalDateTime now = LocalDateTime.now();

        AiDocument document = new AiDocument();
        document.setName(buildDocumentName(stored.originalName()));
        document.setFileName(stored.originalName());
        document.setFileType(stored.fileType());
        document.setFileSize(stored.size());
        document.setFilePath(stored.path().toAbsolutePath().toString());
        document.setStatus(DocumentStatus.PROCESSING);
        document.setChunkCount(0);
        document.setCreateTime(now);
        document.setUpdateTime(now);

        try {
            documentMapper.insert(document);
        } catch (Exception e) {
            fileStorageService.delete(stored.path().toString());
            throw new BizException("保存文档记录失败", e);
        }

        List<Document> chunks = new ArrayList<>();
        try {
            chunks = documentIngestor.parseAndSplit(
                    stored.path(), document.getId(), document.getName());

            vectorStoreService.add(chunks);

            int index = 0;
            for (Document chunk : chunks) {
                AiDocumentChunk row = new AiDocumentChunk();
                row.setDocumentId(document.getId());
                Object metadataIndex = chunk.getMetadata().get("chunkIndex");
                row.setChunkIndex(metadataIndex instanceof Number number
                        ? number.intValue()
                        : index);
                row.setContent(chunk.getText());
                row.setVectorId(chunk.getId());
                row.setCreateTime(LocalDateTime.now());
                chunkMapper.insert(row);
                index++;
            }

            document.setStatus(DocumentStatus.READY);
            document.setChunkCount(chunks.size());
            document.setErrorMessage(null);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);

            return DocumentVO.from(document);
        } catch (Exception e) {
            log.error("文档处理失败 documentId={}", document.getId(), e);
            cleanupFailed(document.getId(), chunks);

            String error = errorText(e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(error);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);

            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException("文档处理失败：" + error, e);
        }
    }

    public List<DocumentVO> list() {
        return documentMapper.selectList(
                        new LambdaQueryWrapper<AiDocument>()
                                .orderByDesc(AiDocument::getCreateTime))
                .stream()
                .map(DocumentVO::from)
                .toList();
    }

    public DocumentVO get(Long id) {
        return DocumentVO.from(requireDocument(id));
    }

    public List<ChunkVO> getChunks(Long id) {
        requireDocument(id);
        return chunkMapper.selectList(
                        new LambdaQueryWrapper<AiDocumentChunk>()
                                .eq(AiDocumentChunk::getDocumentId, id)
                                .orderByAsc(AiDocumentChunk::getChunkIndex))
                .stream()
                .map(ChunkVO::from)
                .toList();
    }

    /** 删除文档、分块、向量和本地文件。 */
    public void delete(Long id) {
        AiDocument document = requireDocument(id);

        List<AiDocumentChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<AiDocumentChunk>()
                        .eq(AiDocumentChunk::getDocumentId, id));

        List<String> vectorIds = chunks.stream()
                .map(AiDocumentChunk::getVectorId)
                .toList();
        vectorStoreService.delete(vectorIds);

        chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getDocumentId, id));
        documentMapper.deleteById(id);
        fileStorageService.delete(document.getFilePath());
    }

    /** 未配置 Key 时直接失败，避免上传请求长时间卡在模型调用重试上。 */
    private void ensureAiConfigured() {
        if (openAiApiKey == null || openAiApiKey.isBlank()
                || "not-configured".equals(openAiApiKey)) {
            throw new BizException("未配置 OPENAI_API_KEY，无法进行文档向量化");
        }
    }

    private AiDocument requireDocument(Long id) {
        AiDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException("文档不存在");
        }
        return document;
    }

    /** 失败清理：删除已写入的向量和分块记录，避免留下半成品数据。 */
    private void cleanupFailed(Long documentId, List<Document> chunks) {
        if (!chunks.isEmpty()) {
            try {
                vectorStoreService.delete(chunks.stream()
                        .map(Document::getId)
                        .toList());
            } catch (Exception e) {
                log.warn("清理失败文档的向量数据异常 documentId={}", documentId, e);
            }
        }
        try {
            chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getDocumentId, documentId));
        } catch (Exception e) {
            log.warn("清理失败文档的分块数据异常 documentId={}", documentId, e);
        }
    }

    private String buildDocumentName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        return name.isBlank() ? "未命名文档" : name;
    }

    private String errorText(Exception e) {
        String message = e.getMessage();
        String text = message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
        return text.length() <= 900 ? text : text.substring(0, 900);
    }
}
