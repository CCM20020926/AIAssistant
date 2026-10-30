# 知识库文档解析服务 — 详细设计文档

## 一、整体架构

```
前端 (Vue3)
  │  POST /api/document/upload (multipart)
  ▼
Nginx
  ├── /api/document/upload  ──▶ Gateway
  │                              ├── 1. 上传文件到 MinIO
  │                              ├── 2. 写入 MySQL document 表 (status=0 pending)
  │                              ├── 3. 发送 Kafka 消息 "parse_task"
  │                              └── 4. 返回 202 Accepted (含 documentId)
  │                                     │
  │                                     ▼ Kafka Topic: document-parse-task
  │
  └── /api/document/parse   ◀── document-parse 模块 (Spring Boot)
       │                         ├── 1. 消费 Kafka "parse_task"
       ▼                         ├── 2. 经 Nginx 调用 MinerU API (轮询 5000/5001)
  MinerU (5000/5001 轮询)        ├── 3. 文本分块 (RecursiveCharacterSplitter)
       │                         ├── 4. 分块写入 MinIO
       ▼                         ├── 5. 更新 MySQL (document.status, chunk 元数据)
  document-parse 继续处理         ├── 6. 计算 embedding → 写入 PostgreSQL (pgvector)
       │                         └── 7. 发送 Kafka "parse_result"
       ▼ Kafka Topic: document-parse-result
  Gateway → SSE/WebSocket → 前端 (绿色成功 / 红色失败)
```

---

## 二、文件目录结构

### 2.1 新增模块 `aiassistant-document-parse`

```
aiassistant-document-parse/
├── pom.xml
├── src/main/java/com/aiassistant/documentparse/
│   ├── DocumentParseApplication.java          # 启动类
│   │
│   ├── config/
│   │   ├── KafkaConfig.java                   # Kafka 消费者/生产者配置
│   │   ├── MinioConfig.java                   # MinIO Client Bean
│   │   ├── MinerUConfig.java                  # MinerU 实例地址配置
│   │   └── PgVectorConfig.java                # PostgreSQL + pgvector 数据源
│   │
│   ├── controller/
│   │   └── ParseController.java               # 接收 Gateway 转发的解析请求（备用同步入口）
│   │
│   ├── consumer/
│   │   └── ParseTaskConsumer.java             # 消费 Kafka parse_task 消息
│   │
│   ├── service/
│   │   ├── DocumentParseService.java          # 编排整个解析流程（核心）
│   │   ├── MinerUService.java                 # 调用 MinerU HTTP API（轮询负载均衡）
│   │   ├── TextChunkService.java              # RecursiveCharacterSplitter 文本分块
│   │   ├── ChunkStorageService.java           # 分块写入 MinIO + MySQL + PostgreSQL
│   │   ── EmbeddingService.java              # 调用 DashScope 计算文本向量
│   │
│   ├── producer/
│   │   └── ParseResultProducer.java           # 向 Kafka 发送解析结果通知
│   │
│   ├── mapper/                                # MyBatis-Plus Mapper (MySQL)
│   │   ├── KnowledgeBaseMapper.java
│   │   ├── DocumentMapper.java
│   │   └── DocumentChunkMapper.java
│   │
│   ├── pgmapper/                              # MyBatis Mapper (PostgreSQL)
│   │   └── ChunkVectorMapper.java
│   │
│   └── dto/
│       ├── ParseTaskMessage.java              # Kafka 任务消息体
│       └── ParseResultMessage.java            # Kafka 结果消息体
│
└── src/main/resources/
    ├── application.yml
    └── mapper/
        ├── KnowledgeBaseMapper.xml
        ├── DocumentMapper.xml
        ├── DocumentChunkMapper.xml
        └── pgmapper/
            └── ChunkVectorMapper.xml
```

### 2.2 其他模块改动文件

```
aiassistant-gateway/
── src/main/java/com/aiassistant/gateway/
│   ├── controller/
│   │   ── DocumentController.java            # 新增：接收文件上传
│   └── service/
│       └── DocumentUploadService.java         # 新增：MinIO上传 + DB写入 + Kafka发送
└── src/main/resources/application.yml         # 新增 document-parse 路由 + Kafka 配置

aiassistant-common/
└── src/main/java/com/aiassistant/common/
    ├── entity/
    │   ├── KnowledgeBase.java                 # 新增
    │   ├── Document.java                      # 新增
    │   ── DocumentChunk.java                 # 新增
    ── dto/
        └── KnowledgeBaseDTO.java              # 新增

aiassistant-frontend/
├── src/components/chat/
│   ├── KnowledgeBasePanel.vue                 # 新增：知识库展示区
│   └── CreateKnowledgeBaseDialog.vue          # 新增：新建知识库对话框
├── src/components/chat/ChatInput.vue          # 修改：知识库模式下显示新建/上传按钮
├── src/api/
│   └── knowledge.js                           # 新增：知识库相关 API
└── src/store/modules/
    └── knowledge.js                           # 新增：知识库状态管理
```

---

## 三、关键类与方法设计

### 3.1 Gateway 层

#### `DocumentController.java`

```java
@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentUploadService uploadService;

    @PostMapping("/upload")
    public R<DocumentUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestHeader("X-User-Id") Long userId) {
        // 1. 校验文件类型和大小
        // 2. 调用 uploadService 执行上传流程
        // 3. 返回 documentId 和初始状态
    }

    @GetMapping("/status/{documentId}")
    public R<DocumentStatusResponse> getStatus(
            @PathVariable Long documentId,
            @RequestHeader("X-User-Id") Long userId) {
        // 查询文档解析状态（供前端轮询或 SSE 推送用）
    }
}
```

#### `DocumentUploadService.java`

```java
@Service
public class DocumentUploadService {

    private final MinioService minioService;
    private final DocumentMapper documentMapper;
    private final KafkaTemplate<String, ParseTaskMessage> kafkaTemplate;

    public DocumentUploadResponse upload(MultipartFile file, Long knowledgeBaseId, Long userId) {
        // 1. 生成 objectName = "docs/{userId}/{knowledgeBaseId}/{uuid}.{ext}"
        // 2. minioService.uploadFile(file, objectName)
        // 3. 构造 Document 实体，status=0 (PENDING)，写入 MySQL
        // 4. 构造 ParseTaskMessage，发送到 Kafka Topic "document-parse-task"
        // 5. 返回 DocumentUploadResponse(documentId, status=PENDING)
    }
}
```

### 3.2 document-parse 模块

#### `ParseTaskConsumer.java`

```java
@Component
public class ParseTaskConsumer {

    private final DocumentParseService parseService;

    @KafkaListener(topics = "document-parse-task", groupId = "document-parse-group")
    public void onParseTask(ParseTaskMessage message) {
        // 调用 parseService.execute(message)
        // 异常时捕获，发送失败结果到 Kafka
    }
}
```

#### `DocumentParseService.java`（核心编排类）

```java
@Service
public class DocumentParseService {

    private final MinerUService minerUService;
    private final TextChunkService textChunkService;
    private final ChunkStorageService chunkStorageService;
    private final DocumentMapper documentMapper;
    private final ParseResultProducer resultProducer;

    public void execute(ParseTaskMessage task) {
        // 1. 更新 document.status = 1 (PARSING)
        // 2. 从 MinIO 下载原始文件
        // 3. 调用 minerUService.parse(file) 获取解析结果（Markdown/文本）
        // 4. 调用 textChunkService.chunk(parseResult) 得到 List<Chunk>
        // 5. 调用 chunkStorageService.saveAll(documentId, knowledgeBaseId, chunks)
        //    - 每个 chunk 写入 MinIO
        //    - 更新 MySQL document.chunk_count, status=2 (SUCCESS)
        //    - 每个 chunk 计算 embedding 后写入 PostgreSQL
        // 6. 发送 ParseResultMessage 到 Kafka "document-parse-result"
        // 异常时: status=3 (FAILED), 记录 error message, 发送失败结果
    }
}
```

#### `MinerUService.java`

```java
@Service
public class MinerUService {

    private final RestTemplate restTemplate;
    // 通过 Nginx 调用，由 Nginx upstream 轮询负载均衡到 5000/5001
    @Value("${mineru.nginx-url:http://127.0.0.1:80/api/document/parse}")
    private String minerUNginxUrl;

    public ParseResult parse(byte[] fileContent, String fileName) {
        // 1. POST multipart 文件到 Nginx /api/document/parse
        //    Nginx upstream mineru_backend 自动轮询到 5000 或 5001
        // 2. 解析返回的 JSON，提取 markdown/text 内容
        // 3. 返回 ParseResult(content, metadata)
        // 异常时抛出 MinerUParseException
    }
}
```

#### `TextChunkService.java`

```java
@Service
public class TextChunkService {

    // 模拟 LangChain RecursiveCharacterSplitter 的实现
    private static final int CHUNK_SIZE = 500;        // 每块目标字符数
    private static final int CHUNK_OVERLAP = 50;       // 块间重叠字符数
    private static final List<String> SEPARATORS = List.of(
        "\n\n", "\n", "。", ".", "！", "!", "？", "?",
        "；", ";", "，", ",", " ", ""
    );

    public List<Chunk> chunk(String text) {
        // 1. 按 SEPARATORS 优先级递归分割文本
        // 2. 合并小块到 CHUNK_SIZE，保留 CHUNK_OVERLAP 重叠
        // 3. 为每个 chunk 分配 index
        // 4. 返回 List<Chunk>(index, content)
    }
}
```

#### `ChunkStorageService.java`

```java
@Service
public class ChunkStorageService {

    private final MinioService minioService;
    private final DocumentChunkMapper chunkMapper;       // MySQL
    private final ChunkVectorMapper vectorMapper;         // PostgreSQL
    private final EmbeddingService embeddingService;

    public void saveAll(Long documentId, Long knowledgeBaseId, List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            // 1. 上传 chunk 文本到 MinIO: "chunks/{documentId}/{index}.txt"
            // 2. 插入 MySQL document_chunk 表（元数据）
            // 3. 调用 embeddingService.embed(chunk.content) 获取向量
            // 4. 插入 PostgreSQL chunk_vector 表（content + embedding）
        }
        // 更新 MySQL document 表: chunk_count = chunks.size(), status = SUCCESS
    }
}
```

#### `EmbeddingService.java`

```java
@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;
    // 使用 DashScope embedding 模型 (text-embedding-v3, 维度 1024)

    public float[] embed(String text) {
        // 1. 调用 DashScope embedding API
        // 2. 返回 float[] 向量
    }
}
```

#### `ParseResultProducer.java`

```java
@Service
public class ParseResultProducer {

    private final KafkaTemplate<String, ParseResultMessage> kafkaTemplate;

    public void sendSuccess(Long documentId, Long userId, int chunkCount) {
        // 发送 ParseResultMessage(status=SUCCESS, chunkCount) 到 "document-parse-result"
    }

    public void sendFailure(Long documentId, Long userId, String errorMessage) {
        // 发送 ParseResultMessage(status=FAILED, errorMessage) 到 "document-parse-result"
    }
}
```

### 3.3 Gateway 消费解析结果

Gateway 需要消费 `document-parse-result` Topic，并通过 SSE 或 WebSocket 通知前端。

```java
// Gateway 中新增
@Component
public class ParseResultConsumer {

    private final SimpMessagingTemplate messagingTemplate;  // 或 SSE 连接管理

    @KafkaListener(topics = "document-parse-result", groupId = "gateway-group")
    public void onParseResult(ParseResultMessage result) {
        // 通过 WebSocket/SSE 推送给对应 userId 的前端
        // 成功: { type: "parse_success", documentId, chunkCount }
        // 失败: { type: "parse_failed", documentId, errorMessage }
    }
}
```

---

## 四、通信流程时序

```
前端          Gateway         MinIO        MySQL        Kafka         document-parse      MinerU       PostgreSQL
 │              │              │            │            │                │                │             │
 │──upload─────▶│              │            │            │                │                │             │
 │              │──putObject──▶│            │            │                │                │             │
 │              │◀─────────────│            │            │                │                │             │
 │              │──INSERT doc─────────────▶│            │                │                │             │
 │              │              │            │            │                │                │             │
 │              │──send parse_task─────────────────────▶│                │                │             │
 │◀──202────────│              │            │            │                │                │             │
 │              │              │            │            │                │                │             │
 │              │              │            │            │──consume──────▶│                │             │
 │              │              │            │            │                │──getObject────▶│             │
 │              │              │            │            │                │◀───────────────│             │
 │              │              │            │            │                │──POST parse──────────────────▶│
 │              │              │            │            │                │◀──markdown────────────────────│
 │              │              │            │            │                │──chunk(text)                   │
 │              │              │            │            │                │                │             │
 │              │              │            │            │                │──putObject(chunks)             │
 │              │              │            │            │                │──INSERT chunks─────────────────────────────▶│
 │              │              │            │            │                │──UPDATE doc status                           │
 │              │              │            │            │                │                │             │
 │              │              │            │            │──send result──│                │             │
 │              │◀──consume─────────────────────────────│                │                │             │
 │──SSE/WS─────│              │            │            │                │                │             │
```

---

## 五、数据库设计

### 5.1 MySQL Schema（元数据）

```sql
-- ============================================
-- 知识库表
-- ============================================
CREATE TABLE knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL COMMENT '所属用户ID',
    name        VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '知识库描述',
    doc_count   INT DEFAULT 0 COMMENT '关联文档数量',
    status      TINYINT DEFAULT 1 COMMENT '1=正常 0=已删除',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_name (user_id, name),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- ============================================
-- 文档表
-- ============================================
CREATE TABLE document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库ID',
    user_id         BIGINT NOT NULL COMMENT '上传用户ID',
    name            VARCHAR(200) NOT NULL COMMENT '原始文件名',
    file_type       VARCHAR(20) DEFAULT NULL COMMENT '文件类型: pdf/docx/md/txt',
    file_size       BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    minio_path      VARCHAR(500) DEFAULT NULL COMMENT 'MinIO 存储路径',
    parse_status    TINYINT DEFAULT 0 COMMENT '0=待解析 1=解析中 2=成功 3=失败',
    parse_error     TEXT DEFAULT NULL COMMENT '解析失败原因',
    chunk_count     INT DEFAULT 0 COMMENT '分块数量',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_knowledge_base (knowledge_base_id),
    INDEX idx_user_id (user_id),
    INDEX idx_parse_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- ============================================
-- 文档分块元数据表
-- ============================================
CREATE TABLE document_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id     BIGINT NOT NULL COMMENT '所属文档ID',
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库ID',
    chunk_index     INT NOT NULL COMMENT '分块序号',
    content_preview VARCHAR(500) DEFAULT NULL COMMENT '内容预览(前500字)',
    minio_path      VARCHAR(500) DEFAULT NULL COMMENT 'MinIO 中分块文本路径',
    token_count     INT DEFAULT 0 COMMENT 'token 数量',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_document (document_id),
    INDEX idx_knowledge_base (knowledge_base_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块元数据表';

-- ============================================
-- 对话表新增字段
-- ============================================
ALTER TABLE conversation
    ADD COLUMN knowledge_base_id BIGINT DEFAULT NULL COMMENT '关联的知识库ID',
    ADD INDEX idx_knowledge_base (knowledge_base_id);
```

### 5.2 PostgreSQL Schema（向量存储）

```sql
-- ============================================
-- 启用 pgvector 扩展
-- ============================================
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- 分块向量表
-- DashScope text-embedding-v3 模型输出维度为 1024
-- ============================================
CREATE TABLE chunk_vector (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL COMMENT '分块完整文本',
    minio_path      VARCHAR(500) DEFAULT NULL,
    embedding       vector(1024) NOT NULL COMMENT '文本向量',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 普通索引
CREATE INDEX idx_cv_document ON chunk_vector(document_id);
CREATE INDEX idx_cv_knowledge_base ON chunk_vector(knowledge_base_id);

-- HNSW 向量索引（推荐用于中等规模数据，查询速度快）
CREATE INDEX idx_cv_embedding_hnsw
    ON chunk_vector
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- 如果数据量超过百万行，可改用 IVFFlat 索引：
-- CREATE INDEX idx_cv_embedding_ivf
--     ON chunk_vector
--     USING ivfflat (embedding vector_cosine_ops)
--     WITH (lists = 100);
```

### 5.3 向量检索示例 SQL

```sql
-- 在指定知识库中检索与 query_embedding 最相似的 top-K 分块
SELECT
    id,
    document_id,
    chunk_index,
    content,
    1 - (embedding <=> $1::vector) AS similarity
FROM chunk_vector
WHERE knowledge_base_id = $2
ORDER BY embedding <=> $1::vector
LIMIT $3;
```

---

## 六、Kafka Topic 设计

| Topic | 生产者 | 消费者 | 消息体 | 用途 |
|-------|--------|--------|--------|------|
| `document-parse-task` | Gateway | document-parse | `ParseTaskMessage` | 通知开始解析 |
| `document-parse-result` | document-parse | Gateway | `ParseResultMessage` | 通知解析完成/失败 |

### `ParseTaskMessage`

```java
public class ParseTaskMessage {
    private Long documentId;
    private Long knowledgeBaseId;
    private Long userId;
    private String minioPath;      // 原始文件在 MinIO 的路径
    private String fileName;
    private String fileType;
}
```

### `ParseResultMessage`

```java
public class ParseResultMessage {
    private Long documentId;
    private Long userId;
    private String status;          // "SUCCESS" / "FAILED"
    private Integer chunkCount;     // 成功时分块数
    private String errorMessage;    // 失败时错误信息
}
```

---

## 七、前端设计

### 7.1 ChatInput.vue 改动

在现有 switch 按钮上方，当"知识库"开关打开时，显示两个操作按钮：

```
┌─────────────────────────────────────────────┐
│  [+ 新建知识库]  [↑ 上传文档]    ← 知识库模式下显示  │
│                                             │
│  ○ 深度思考  ○ 联网搜索  ○ PPT  ○ 知识库    │
│                                             │
│  ┌─────────────────────────────┐  [发送]    │
│  │ 输入消息...                  │            │
│  └─────────────────────────────┘            │
│  Enter 发送，Shift+Enter 换行                │
└─────────────────────────────────────────────┘
```

### 7.2 KnowledgeBasePanel.vue（新增组件）

位于对话栏下方，展示知识库列表：

```
─────────────────────────────────────────────┐
│  知识库                                       │
│  ┌─────────────────────────────────────────┐ │
│  │ 📚 Java面试知识点                          │ │
│  │   ├── 📄 Spring核心原理.pdf  [32块] [✓]   │ │
│  │   ├── 📄 JVM调优指南.docx  [18块] [✓]    │ │
│  │   └── 关联对话: 3个                       │ │
│  ├─────────────────────────────────────────┤ │
│  │ 📚 Python学习笔记                          │ │
│  │   ├── 📄 异步编程.md  [12块] [✓]         │ │
│  │   ── 关联对话: 1个                       │ │
│  └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

### 7.3 前端 API (`knowledge.js`)

```javascript
// 知识库 CRUD
export function createKnowledgeBase(name, description)
export function getKnowledgeBaseList()
export function deleteKnowledgeBase(id)

// 文档管理
export function uploadDocument(file, knowledgeBaseId)
export function getDocumentList(knowledgeBaseId)
export function deleteDocument(id)
export function getDocumentStatus(documentId)   // 轮询解析状态

// 对话关联
export function linkConversationToKnowledgeBase(conversationId, knowledgeBaseId)
```

---

## 八、配置项汇总

### Gateway `application.yml` 新增

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: document-upload
          uri: lb://aiassistant-agent
          predicates:
            - Path=/api/document/upload
        - id: document-parse
          uri: lb://aiassistant-document-parse
          predicates:
            - Path=/api/document/parse

  kafka:
    bootstrap-servers: ${KAFKA_ADDR:127.0.0.1:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

minio:
  endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
  accessKey: ${MINIO_ACCESS_KEY:minioadmin}
  secretKey: ${MINIO_SECRET_KEY:minioadmin}
  bucketName: aiassistant
```

### document-parse `application.yml`

```yaml
server:
  port: 8082

spring:
  application:
    name: aiassistant-document-parse

  datasource:
    url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/ai_assistant?...
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}

  kafka:
    bootstrap-servers: ${KAFKA_ADDR:127.0.0.1:9092}
    consumer:
      group-id: document-parse-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.aiassistant.common.dto

  # PostgreSQL 数据源（向量存储）
  pg-datasource:
    url: jdbc:postgresql://${PG_HOST:127.0.0.1}:${PG_PORT:5432}/ai_assistant_vectors
    username: ${PG_USER:postgres}
    password: ${PG_PASSWORD:postgres}

minio:
  endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
  accessKey: ${MINIO_ACCESS_KEY:minioadmin}
  secretKey: ${MINIO_SECRET_KEY:minioadmin}
  bucketName: aiassistant

mineru:
  instances: ${MINERU_INSTANCES:http://127.0.0.1:5000,http://127.0.0.1:5001}

ai:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    embedding-model: text-embedding-v3
```

---

## 九、Nginx 配置改动

Nginx 需要新增两条路由规则，并调整文件上传限制。

### 9.1 新增 upstream：MinerU 实例负载均衡

```nginx
upstream mineru_backend {
    # 轮询负载均衡，两个 MinerU 实例
    server 127.0.0.1:5000;
    server 127.0.0.1:5001;
}
```

### 9.2 新增 location 块

```nginx
server {
    listen 80;
    server_name localhost;

    # ===== 已有配置保持不变 =====
    # /api/chat/stream  → Gateway (SSE)
    # /api/chat/**      → Gateway
    # /api/conversation/** → Gateway
    # 静态资源 → 前端 dist

    # ===== 新增：文档上传 → Gateway =====
    # 前端上传文件，经 Nginx 转发到 Gateway
    # Gateway 负责上传 MinIO + 写 DB + 发 Kafka
    location /api/document/upload {
        proxy_pass http://gateway_backend;  # 复用已有 Gateway upstream

        # 文件上传相关
        client_max_body_size 100m;          # 允许最大 100MB 文件
        proxy_read_timeout 120s;            # 上传可能较慢，延长超时
        proxy_connect_timeout 10s;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Authorization $http_authorization;
    }

    # ===== 新增：文档解析 → MinerU 集群 =====
    # document-parse 模块消费 Kafka 后，向 Nginx 发请求
    # Nginx 负载均衡到 MinerU 5000/5001 实例
    location /api/document/parse {
        proxy_pass http://mineru_backend;

        # MinerU 解析大文件可能耗时较长
        proxy_read_timeout 300s;
        proxy_connect_timeout 10s;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # ===== 新增：文档状态查询 → Gateway =====
    location /api/document/status {
        proxy_pass http://gateway_backend;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header Authorization $http_authorization;
    }
}
```

### 9.3 改动要点总结

| 改动项 | 说明 |
|--------|------|
| 新增 `upstream mineru_backend` | 轮询负载均衡两个 MinerU 实例 (5000/5001) |
| 新增 `location /api/document/upload` | 前端文件上传 → Gateway，需设置 `client_max_body_size 100m` |
| 新增 `location /api/document/parse` | document-parse → MinerU，需设置 `proxy_read_timeout 300s`（解析耗时长） |
| 新增 `location /api/document/status` | 前端轮询解析状态 → Gateway |
| `client_max_body_size` | 默认 1MB，必须调大到 100MB 以支持大文件上传 |

### 9.4 通信路径说明

```
路径 1：前端上传
  前端 → Nginx /api/document/upload → Gateway → MinIO + MySQL + Kafka

路径 2：文档解析（内部调用）
  document-parse → Nginx /api/document/parse → MinerU (5000/5001 轮询)

路径 3：状态查询
  前端 → Nginx /api/document/status/{id} → Gateway → MySQL
```

> **为什么 document-parse 不直接调用 MinerU，而要经过 Nginx？**
> 1. 统一入口管理，便于后续 MinerU 实例扩缩容时只改 Nginx 配置
> 2. Nginx 提供内置的负载均衡和健康检查能力
> 3. 便于统一监控和日志采集

---

## 十、异常处理策略

| 异常场景 | 处理方式 |
|----------|----------|
| 文件上传 MinIO 失败 | Gateway 直接返回错误，不发送 Kafka 消息 |
| MinerU 两个实例都不可用 | 重试 3 次，间隔递增；最终标记 document.status=3 |
| MinerU 解析返回空内容 | 标记失败，记录错误信息 |
| 文本分块结果为空 | 标记失败 |
| Embedding API 调用失败 | 单块重试 2 次；全部失败则标记文档失败 |
| PostgreSQL 写入失败 | 回滚该文档所有已写入的 chunk，标记失败 |
| Kafka 消息发送失败 | Gateway 本地记录，定时补偿重发 |

---

## 十一、后续扩展点

1. **对话中引用知识库**：用户发送消息时，若关联了知识库，先从 PostgreSQL 检索相关 chunk 作为上下文注入 Prompt
2. **分块策略可配置**：不同文档类型使用不同的 chunk_size（代码 vs 论文 vs 普通文本）
3. **MinerU 健康检查**：定期探测 5000/5001 实例健康状态，自动剔除不可用节点
4. **解析进度推送**：通过 Kafka 分阶段报告进度（下载→解析→分块→向量化→完成）
