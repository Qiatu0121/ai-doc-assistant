# AI 技术文档助手 · 文档模块 + Agent

当前实现：

- 文档模块：上传、Tika 解析、分块、Embedding、Elasticsearch 向量库入库、列表、详情、分块查看、删除。
- Agent 模块：模型通过 Spring AI Function Calling 自行选择工具，支持知识库检索和错误日志分析。

暂未实现多轮会话持久化和流式输出。

## 技术栈

- Java 17
- Spring Boot 3.5.15
- Spring AI 1.1.8
- MyBatis-Plus 3.5.12
- MySQL 8
- Elasticsearch 8.x（向量检索，Spring AI Elasticsearch VectorStore）
- Spring Data Elasticsearch

## 环境变量与配置

敏感配置不写入仓库，使用环境变量或本地私有配置：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `DB_URL` | 否 | 默认本机 `ai_doc_assistant` 数据库 URL |
| `DB_USERNAME` | 是 | 数据库账号 |
| `DB_PASSWORD` | 是 | 数据库密码 |
| `OPENAI_API_KEY` | 真实模型时必填 | 未配置时文档索引和 Agent 会快速失败 |
| `OPENAI_BASE_URL` | 否 | 默认 `https://api.openai.com`，可配置兼容接口 |
| `CHAT_MODEL` | 否 | 默认 `gpt-4o-mini` |
| `EMBEDDING_MODEL` | 否 | 默认 `text-embedding-3-small` |
| `ES_URIS` | 否 | 默认 `http://localhost:9200` |
| `ES_USERNAME` | 否 | ES 开启 security 时填写 |
| `ES_PASSWORD` | 否 | ES 开启 security 时填写 |
| `ES_INDEX_NAME` | 否 | 默认 `ai-doc-assistant` |
| `ES_VECTOR_DIMENSIONS` | 否 | 默认 `1536`，必须与 Embedding 模型一致 |
| `ES_SIMILARITY` | 否 | 默认 `cosine` |
| `SERVER_PORT` | 否 | 默认 `8080` |

本地文件配置：

```bash
cp application-local.yml.example application-local.yml
# 填入真实数据库和模型配置后启动
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=file:./application-local.yml"
```

`application-local.yml` 已被 `.gitignore` 忽略。

上传目录：

```text
./data/uploads
```

Elasticsearch：

- 需要可访问的 Elasticsearch 8.x，默认地址 `http://localhost:9200`
- 索引默认 `ai-doc-assistant`，由 `initialize-schema: true` 首次自动创建
- `ES_VECTOR_DIMENSIONS` 必须与 Embedding 模型一致：
  - OpenAI `text-embedding-3-small`：1536
  - 本地假 Embedding 服务：8
- 如果更换 Embedding 模型，需要删除旧索引后重新建立

> Spring AI 2.x 面向 Spring Boot 4，本项目使用 Spring Boot 3.x，因此使用 Spring AI 1.1.x。

## 建库

```bash
mysql -u "<db-user>" -p ai_doc_assistant < src/main/resources/db/schema.sql
```

## 运行

```bash
cd <project-dir>
mvn spring-boot:run
```

默认端口 8080。如果 8080 被占用：

```bash
SERVER_PORT=8081 mvn spring-boot:run
```

## 接口测试

```bash
# 上传
curl -F "file=@samples/sample.md" http://localhost:8080/api/documents

# 列表
curl http://localhost:8080/api/documents

# 详情
curl http://localhost:8080/api/documents/1

# 分块
curl http://localhost:8080/api/documents/1/chunks

# 删除
curl -X DELETE http://localhost:8080/api/documents/1
```

## Agent 接口

```bash
curl -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @samples/agent/knowledge.json \
  http://localhost:8080/api/agent/chat
```

响应包含：

- `answer`：模型最终回答
- `toolCalls`：每次工具调用的参数、耗时、结果摘要、成功/失败
- `toolCallCount` / `maxToolCalls` / `limitExceeded`

## 两个真实工具

| 工具名 | 用途 | 参数 |
| --- | --- | --- |
| `searchKnowledgeBase` | 检索已上传的文档知识库 | `query` 必填；`topK` 可选 1-10 |
| `analyzeErrorLog` | 分析错误日志、异常堆栈、Caused by 链 | `logText` 必填；`maxLines` 可选 1-20 |

工具参数由模型填写，后端负责：

- 参数格式和范围校验
- 单次请求最大工具调用次数限制（默认 6，后端强制）
- 每次调用日志
- 工具异常转换为结构化错误返回给模型
- 超限异常中断 Agent 循环并返回受控提示

**工具是否调用、调用哪一个、调用几次的参数由模型决定。生产代码没有按关键词 if-else 路由工具。**
`tools/fake_embedding_server.py` 里的分支只是一个“假模型”测试脚本，用于没有 Key 时验证链路。

## 决策边界

| 决策 | 由谁负责 |
| --- | --- |
| 是否需要工具 | 模型 |
| 调用哪个工具 | 模型 |
| 工具参数填什么 | 模型 |
| 是否继续多轮工具调用 | 模型（受后端上限约束） |
| 工具定义和 JSON Schema | 后端 |
| 参数校验 | 后端工具方法 |
| 最大调用次数 | 后端 `CountingLoggingToolCallback` |
| 调用日志 | 后端装饰器 |
| 工具异常是否回传给模型 | 后端包装器 |
| 超限后终止 Agent | 后端 AgentService |
| 最终自然语言回答 | 模型 |

## Agent 配置

```yaml
app:
  agent:
    max-tool-calls: 6
    default-top-k: 4
    similarity-threshold: 0.3

spring:
  ai:
    tools:
      # 让最大调用次数异常中断 Agent 循环，而不是被当作普通工具错误
      throw-exception-on-error: true
```

## 没有 API Key 时验证链路

项目附带一个本地假 OpenAI 兼容服务（同时实现 Embedding 和 Chat Completions）：

```bash
python tools/fake_embedding_server.py
```

另开终端启动应用：

```bash
SERVER_PORT=8082 \
DB_USERNAME=<db-user> \
DB_PASSWORD=<db-password> \
OPENAI_API_KEY=test \
OPENAI_BASE_URL=http://localhost:9999 \
EMBEDDING_MODEL=fake-embedding \
CHAT_MODEL=fake-chat \
ES_VECTOR_DIMENSIONS=8 \
mvn spring-boot:run
```

先上传一篇文档：

```bash
curl -F "file=@samples/sample.md" http://localhost:8082/api/documents
```

再执行 Agent 测试：

```bash
curl -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @samples/agent/knowledge.json \
  http://localhost:8082/api/agent/chat

curl -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @samples/agent/error-log.json \
  http://localhost:8082/api/agent/chat

curl -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @samples/agent/hello.json \
  http://localhost:8082/api/agent/chat

curl -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @samples/agent/invalid-args.json \
  http://localhost:8082/api/agent/chat

curl -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @samples/agent/limit.json \
  http://localhost:8082/api/agent/chat
```

假模型测试脚本的策略不是生产逻辑；真实模型使用相同工具定义，自行决定是否调用工具。

## 核心调用链

```
DocumentController.upload
  → DocumentService.upload
    → FileStorageService.save                 校验并保存原始文件
    → DocumentIngestor.parseAndSplit          Tika 解析 + TokenTextSplitter 分块
    → VectorStoreService.add                  Embedding + ElasticsearchVectorStore.add
    → AiDocumentChunkMapper.insert            保存分块原文和向量 ID
    → AiDocumentMapper.updateById             更新为 READY
```

```
DocumentController.delete
  → DocumentService.delete
    → VectorStoreService.delete               按向量 ID 从 Elasticsearch 删除
    → AiDocumentChunkMapper.delete
    → AiDocumentMapper.deleteById
    → FileStorageService.delete
```

## 测试

```bash
mvn test
```

当前测试：

- `ErrorLogToolTest`：3 个
- `KnowledgeBaseToolTest`：3 个
- `CountingLoggingToolCallbackTest`：2 个

共 8 个测试通过。单元测试不启动 Spring 上下文，不需要 MySQL、Elasticsearch 或 OpenAI。

完整应用启动需要 MySQL、Elasticsearch 8.x，以及可用的 OpenAI 兼容模型或本地假模型服务。

## 常见异常

- 文件为空、超过 10MB、扩展名不支持：上传前校验失败
- Tika 解析不出文本：文档标记 FAILED，返回“文档中没有可解析的文本”
- 未配置 `OPENAI_API_KEY`：快速失败，不创建文档记录
- Embedding 接口 401/网络异常：文档标记 FAILED，保留错误信息
- Elasticsearch 不可用、索引创建失败或向量维度不匹配：启动/检索失败并返回错误
- 文档不存在或 ID 非数字：统一错误响应
- 删除时向量库操作失败：数据库和文件暂不删除，可重试
