package com.example.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_document_chunk 表：文档分块原文与向量 ID。
 */
@Data
@TableName("ai_document_chunk")
public class AiDocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private String vectorId;

    private LocalDateTime createTime;
}
