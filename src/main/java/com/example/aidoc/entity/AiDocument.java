package com.example.aidoc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aidoc.enums.DocumentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_document 表：文档元数据与处理状态。
 */
@Data
@TableName("ai_document")
public class AiDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String filePath;

    private DocumentStatus status;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
