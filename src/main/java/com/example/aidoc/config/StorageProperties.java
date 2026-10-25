package com.example.aidoc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储配置。
 */
@Data
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 原始文档保存目录 */
    private String uploadDir = "./data/uploads";
}
