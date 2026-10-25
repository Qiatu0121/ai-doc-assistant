package com.example.aidoc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 技术文档助手启动类。
 */
@MapperScan("com.example.aidoc.mapper")
@SpringBootApplication
public class AiDocAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDocAssistantApplication.class, args);
    }
}
