-- AI 技术文档助手：文档模块建表脚本
CREATE DATABASE IF NOT EXISTS ai_doc_assistant
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ai_doc_assistant;

CREATE TABLE IF NOT EXISTS ai_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL COMMENT '展示名称',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(50) COMMENT 'pdf/md/txt/docx',
    file_size BIGINT COMMENT '字节数',
    file_path VARCHAR(500) NOT NULL COMMENT '本地存储路径',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0处理中 1已就绪 2失败',
    chunk_count INT NOT NULL DEFAULT 0 COMMENT '分块数量',
    error_message VARCHAR(1000) COMMENT '失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_document_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL COMMENT '文档内序号，从 0 开始',
    content LONGTEXT NOT NULL COMMENT '分块原文',
    vector_id VARCHAR(64) NOT NULL COMMENT 'SimpleVectorStore 中的向量 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vector_id (vector_id),
    KEY idx_document_id (document_id)
);
