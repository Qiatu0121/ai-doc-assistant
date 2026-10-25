package com.example.aidoc.dto;

import com.example.aidoc.entity.AiDocumentChunk;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块返回视图。
 */
@Data
public class ChunkVO {

    private Long id;

    private Integer chunkIndex;

    private String content;

    private LocalDateTime createTime;

    public static ChunkVO from(AiDocumentChunk chunk) {
        ChunkVO vo = new ChunkVO();
        vo.setId(chunk.getId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setCreateTime(chunk.getCreateTime());
        return vo;
    }
}
