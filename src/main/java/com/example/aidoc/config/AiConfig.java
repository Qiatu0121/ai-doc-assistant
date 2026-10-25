package com.example.aidoc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI 相关 Bean 配置。
 * SimpleVectorStore 使用内存 + JSON 文件持久化，适合单实例、小规模文档场景。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class AiConfig {

    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel,
                                               StorageProperties properties) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        Path file = Paths.get(properties.getVectorStoreFile()).toAbsolutePath().normalize();

        if (Files.exists(file)) {
            try {
                store.load(file.toFile());
                log.info("已加载向量库文件：{}", file);
            } catch (Exception e) {
                // 文件损坏时直接失败，避免应用正常启动但检索结果为空
                throw new IllegalStateException("向量库文件加载失败：" + file, e);
            }
        }
        return store;
    }
}
