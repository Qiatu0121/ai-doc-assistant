package com.example.aidoc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类入口。
 * ElasticsearchVectorStore 由 Spring AI 的 Elasticsearch starter 自动配置，
 * 应用只保留本地文件存储属性。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class AiConfig {
}
