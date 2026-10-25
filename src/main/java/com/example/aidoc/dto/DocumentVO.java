package com.example.aidoc.dto;

import com.example.aidoc.entity.AiDocument;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档返回视图。
 */
@Data
public class DocumentVO {

    private Long id;

    private String name;

    private String fileName;

    private String fileType;

    private Long fileSize;

    /** PROCESSING / READY / FAILED */
    private String status;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createTime;

    public static DocumentVO from(AiDocument document) {
        DocumentVO vo = new DocumentVO();
        vo.setId(document.getId());
        vo.setName(document.getName());
        vo.setFileName(document.getFileName());
        vo.setFileType(document.getFileType());
        vo.setFileSize(document.getFileSize());
        vo.setStatus(document.getStatus() == null ? null : document.getStatus().name());
        vo.setChunkCount(document.getChunkCount());
        vo.setErrorMessage(document.getErrorMessage());
        vo.setCreateTime(document.getCreateTime());
        return vo;
    }
}
