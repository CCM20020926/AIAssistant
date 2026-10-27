# AI Assistant 开发计划 — 日常聊天 + 网关模块 + 前端模块

> 范围：gateway / agent（仅 ChatAgent 日常聊天部分）/ 前端（Vue 3）
> 版本基线：严格遵循设计文档 1.0-snapshot

---

## 一、框架与技术栈版本

### 1.1 后端（严格锁定文档版本）

| 组件 | 版本                     | 说明 |
|---|------------------------|---|
| JDK | 21 (LTS)               | 虚拟线程支持 |
| Spring Boot | 3.3.4                  | 应用框架 |
| Spring Cloud | 2023.0.3               | Gateway / LoadBalancer / OpenFeign |
| Spring Cloud Alibaba | 2023.0.1.2             | Nacos 注册/配置 |
| Spring AI | 1.0.0                  | LLM 抽象层 |
| Spring AI Alibaba | 1.0.0.2                | DashScope 封装 |
| Sa-Token | 1.37.0 (boot3-starter) | 鉴权 |
| MyBatis-Plus | 3.5.7                  | ORM（Boot 3.3 兼容） |
| MySQL Connector | Boot 托管 (8.0.x)        | 驱动 |
| Redis (Lettuce) | Boot 托管                | 缓存/限流/Token |
| Kafka (spring-kafka) | Boot 托管 (3.1.x client) | 异步消息 |
| Nacos Server | 2.4.2                  | 注册/配置中心 |
| Redis Server | 7.4.x                  | 缓存 |
| MySQL | 8.0.36+                | 关系库 |
| **Nginx** | **1.29**                 | 反向代理 / TLS / SSE 透传 / 限流 |

### 1.2 前端（兼容 Node 18.18.0 / npm 9.2.0）

| 组件 | 版本 | 说明 |
|---|---|---|
| Node.js | 18.18.0 (LTS) | 用户指定 |
| npm | 9.2.0 | 随 Node 18.18.0 附带 |
| Vue | 3.4.x | 渐进式框架 |
| Vite | 5.x | 构建工具（5.x 支持 Node 18） |
| Pinia | 2.x | 状态管理 |
| Vue Router | 4.x | 路由 |
| Element Plus | 2.x | UI 组件库 |
| Axios | 1.x | HTTP 客户端 |
| @microsoft/fetch-event-source | 2.x | SSE 客户端 |
| markdown-it | 14.x | Markdown 渲染 |
| highlight.js | 11.x | 代码高亮 |

> **注意**：Vite 5.x 要求 Node >= 18，满足要求。若使用 Vite 6.x 则要求 Node >= 20，不可用。

---

## 二、MySQL 数据库 Schema 设计

### 2.1 核心表 DDL

```sql
-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE `user` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(64)      NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(255)     NOT NULL COMMENT 'BCrypt 哈希',
    `nickname`      VARCHAR(64)      DEFAULT NULL COMMENT '昵称',
    `avatar_url`    VARCHAR(512)     DEFAULT NULL COMMENT '头像',
    `status`        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-正常 0-禁用',
    `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 会话表
-- ============================================================
CREATE TABLE `conversation` (
    `id`         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED  NOT NULL COMMENT '所属用户',
    `title`      VARCHAR(256)     NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `mode`       VARCHAR(32)      NOT NULL DEFAULT 'chat' COMMENT 'chat/web_search/ppt/knowledge',
    `status`     TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-活跃 0-归档 2-删除',
    `created_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 用户查自己的会话列表：高频查询
    KEY `idx_user_updated` (`user_id`, `status`, `updated_at` DESC),
    KEY `idx_user_created` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ============================================================
-- 消息表（数据量最大的表，重点优化）
-- ============================================================
CREATE TABLE `message` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED  NOT NULL COMMENT '所属会话',
    `user_id`         BIGINT UNSIGNED  NOT NULL COMMENT '所属用户（冗余，加速查询）',
    `role`            VARCHAR(32)      NOT NULL COMMENT 'user/assistant/system',
    `content`         LONGTEXT         NOT NULL COMMENT '消息内容',
    `meta`            JSON             DEFAULT NULL COMMENT '元数据（thinking/citations/tool_calls）',
    `token_count`     INT UNSIGNED     DEFAULT 0 COMMENT 'token 消耗',
    `seq_no`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '会话内消息序号',
    `created_at`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 加载某会话的消息列表：最核心查询
    KEY `idx_conv_seq` (`conversation_id`, `seq_no`),
    -- 用户维度统计
    KEY `idx_user_created` (`user_id`, `created_at`),
    -- 按会话统计消息数
    KEY `idx_conv_created` (`conversation_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ============================================================
-- 知识库表
-- ============================================================
CREATE TABLE `knowledge_base` (
    `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED  NOT NULL COMMENT '所属用户',
    `name`        VARCHAR(128)     NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(512)     DEFAULT NULL COMMENT '描述',
    `doc_count`   INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '文档数量（冗余计数）',
    `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-正常 0-删除',
    `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- ============================================================
-- 文档表（双归属：conversation_id 或 knowledge_base_id 二选一）
-- ============================================================
CREATE TABLE `document` (
    `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT UNSIGNED  NOT NULL,
    `conversation_id`  BIGINT UNSIGNED  DEFAULT NULL COMMENT '对话上传时填写',
    `knowledge_base_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '知识库上传时填写',
    `file_name`        VARCHAR(256)     NOT NULL COMMENT '原始文件名',
    `file_url`         VARCHAR(512)     NOT NULL COMMENT 'MinIO 地址',
    `file_size`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
    `file_type`        VARCHAR(16)      NOT NULL COMMENT 'pdf/docx/txt/md',
    `parse_status`     VARCHAR(32)      NOT NULL DEFAULT 'pending' COMMENT 'pending/parsing/done/failed',
    `chunk_count`      INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '分块数量',
    `created_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 按知识库查文档
    KEY `idx_kb_status` (`knowledge_base_id`, `parse_status`),
    -- 按对话查文档
    KEY `idx_conv` (`conversation_id`),
    -- 按用户查
    KEY `idx_user` (`user_id`, `created_at` DESC),
    -- 互斥约束（应用层校验 + 数据库 CHECK）
    CONSTRAINT `chk_doc_belonging` CHECK (
        (`conversation_id` IS NOT NULL AND `knowledge_base_id` IS NULL) OR
        (`conversation_id` IS NULL AND `knowledge_base_id` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- ============================================================
-- PPT 任务表
-- ============================================================
CREATE TABLE `ppt_task` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED  NOT NULL COMMENT '绑定会话',
    `user_id`         BIGINT UNSIGNED  NOT NULL,
    `status`          VARCHAR(32)      NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/done/failed',
    `title`           VARCHAR(256)     DEFAULT NULL COMMENT 'PPT 标题',
    `file_url`        VARCHAR(512)     DEFAULT NULL COMMENT 'MinIO 成品地址',
    `error_msg`       VARCHAR(1024)    DEFAULT NULL,
    `created_at`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conv_status` (`conversation_id`, `status`),
    KEY `idx_user_created` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PPT任务表';
```

### 2.2 大数据量优化策略

| 策略 | 实施方式 | 适用场景 |
|---|---|---|
| **复合索引优化** | `idx_user_updated(user_id, status, updated_at DESC)` | 用户查会话列表，覆盖索引避免回表 |
| **冗余 user_id** | message 表冗余 user_id 字段 | 避免 JOIN conversation 即可按用户查询/统计 |
| **分页用游标** | `WHERE id < #{lastId} ORDER BY id DESC LIMIT 20` | 消息历史加载，避免 OFFSET 深分页 |
| **JSON 列存 meta** | citations/tool_calls 存 JSON 而非子表 | 减少 JOIN，按需解析 |
| **冗余计数** | knowledge_base.doc_count | 避免 COUNT(*) 聚合 |
| **未来分表** | message 按 conversation_id HASH 分表 | 单表超 5000 万行时启用 ShardingSphere |
| **冷热分离** | 超过 90 天的会话归档到 conversation_archive | 保持热表精简 |

### 2.3 关键查询示例

```sql
-- 用户会话列表（游标分页）
SELECT id, title, mode, updated_at
FROM conversation
WHERE user_id = ? AND status = 1 AND updated_at < ?
ORDER BY updated_at DESC
LIMIT 20;

-- 加载会话消息（游标分页，正向）
SELECT id, role, content, meta, seq_no, created_at
FROM message
WHERE conversation_id = ? AND seq_no > ?
ORDER BY seq_no ASC
LIMIT 50;

-- 用户知识库列表
SELECT id, name, description, doc_count, created_at
FROM knowledge_base
WHERE user_id = ? AND status = 1
ORDER BY updated_at DESC;
```

---

## 三、项目目录结构

### 3.1 后端（Maven 多模块）

```
AIAssistant/
├── pom.xml                              # 父 POM（BOM 管理版本）
├── aiassistant-common/                  # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/aiassistant/common/
│       ├── config/                      # 通用配置
│       │   ├── RedisConfig.java
│       │   ├── JacksonConfig.java
│       │   └── MyBatisPlusConfig.java
│       ├── exception/                   # 统一异常
│       │   ├── BusinessException.java
│       │   ├── ErrorCode.java
│       │   └── GlobalExceptionHandler.java
│       ├── result/                      # 统一响应
│       │   └── R.java                   # Result<T>
│       ├── entity/                      # 公共实体基类
│       │   └── BaseEntity.java
│       ├── constant/                    # 常量
│       │   └── CommonConstants.java
│       └── util/                        # 工具类
│           └── TraceIdUtil.java
│
├── aiassistant-gateway/                 # 网关模块（含登录/注册）
│   ├── pom.xml
│   └── src/main/java/com/aiassistant/gateway/
│       ├── GatewayApplication.java
│       ├── config/
│       │   ├── RouteConfig.java         # 路由配置
│       │   ├── SaTokenConfig.java       # Sa-Token 集成
│       │   ├── RateLimiterConfig.java   # 限流配置
│       │   └── CorsConfig.java          # 跨域
│       ├── filter/
│       │   ├── AuthGlobalFilter.java    # 鉴权过滤器
│       │   ├── RateLimitFilter.java     # 限流过滤器
│       │   └── TraceIdFilter.java       # traceId 透传
│       ├── controller/
│       │   └── AuthController.java      # 登录/注册/登出（Gateway 直接处理）
│       ├── service/
│       │   └── AuthService.java         # 认证逻辑（BCrypt 校验 + Sa-Token 签发）
│       ├── mapper/
│       │   └── UserMapper.java          # 用户表查询（Gateway 直连 MySQL）
│       ├── entity/
│       │   └── User.java
│       ├── dto/
│       │   ├── LoginRequest.java
│       │   ├── RegisterRequest.java
│       │   └── LoginResponse.java
│       ├── handler/
│       │   └── GatewayFallbackHandler.java  # 降级处理
│       └── resources/
│           ├── application.yml
│           └── bootstrap.yml            # Nacos 配置
│
├── aiassistant-agent/                   # Agent 模块（本期仅 ChatAgent）
│   ├── pom.xml
│   └── src/main/java/com/aiassistant/agent/
│       ├── AgentApplication.java
│       ├── config/
│       │   ├── DashScopeConfig.java     # DashScope 配置
│       │   ├── KafkaConfig.java
│       │   └── SseConfig.java
│       ├── controller/
│       │   ├── ChatController.java      # 对话接口
│       │   └── ConversationController.java  # 会话管理
│       ├── service/
│       │   ├── ChatService.java         # 对话核心逻辑
│       │   ├── ConversationService.java # 会话 CRUD
│       │   └── MessageService.java      # 消息持久化
│       ├── agent/
│       │   └── ChatAgent.java           # 日常对话 Agent
│       ├── mapper/
│       │   ├── ConversationMapper.java
│       │   └── MessageMapper.java
│       ├── entity/
│       │   ├── Conversation.java
│       │   └── Message.java
│       ├── dto/
│       │   ├── ChatRequest.java
│       │   ├── ConversationDTO.java
│       │   └── MessageDTO.java
│       └── resources/
│           ├── application.yml
│           ├── bootstrap.yml
│           └── mapper/                  # MyBatis XML（如需）
│
├── aiassistant-document-parse/          # 文档解析（本期不开发）
└── aiassistant-ppt-rend/               # PPT 渲染（本期不开发）
```

### 3.2 前端

```
aiassistant-web/
├── package.json
├── vite.config.js
├── index.html
├── .env.development
├── .env.production
├── public/
│   └── favicon.ico
└── src/
    ├── main.js
    ├── App.vue
    ├── router/
    │   └── index.js                     # 路由配置
    ├── store/
    │   ├── index.js                     # Pinia 入口
    │   ├── modules/
    │   │   ├── user.js                  # 用户状态
    │   │   ├── conversation.js          # 会话列表
    │   │   └── chat.js                  # 当前对话消息
    │   └── index.js
    ├── api/
    │   ├── request.js                   # Axios 实例（拦截器/Token）
    │   ├── auth.js                      # 登录/注册 API
    │   ├── conversation.js              # 会话 CRUD API
    │   └── chat.js                      # 对话 API（含 SSE）
    ├── utils/
    │   ├── sse.js                       # SSE 封装（fetch-event-source）
    │   ├── storage.js                   # localStorage 封装
    │   └── markdown.js                  # markdown-it 配置
    ├── views/
    │   ├── login/
    │   │   └── LoginView.vue
    │   ├── register/
    │   │   └── RegisterView.vue
    │   └── chat/
    │       └── ChatView.vue             # 主聊天页
    ├── components/
    │   ├── layout/
    │   │   ├── Sidebar.vue              # 左侧栏（会话历史）
    │   │   └── Header.vue
    │   ├── chat/
    │   │   ├── MessageList.vue          # 消息列表
    │   │   ├── MessageItem.vue          # 单条消息（Markdown 渲染）
    │   │   ├── ChatInput.vue            # 输入栏 + 开关
    │   │   ├── WelcomePage.vue          # 欢迎页
    │   │   └── ThinkingBlock.vue        # 思考内容块
    │   └── common/
    │       └── LoadingSpinner.vue
    ├── styles/
    │   ├── index.scss
    │   ├── variables.scss
    │   └── chat.scss
    └── assets/
        └── images/
            └── logo.svg
```

---

## 四、关键类设计与接口

### 4.1 Gateway 模块

#### 4.1.1 AuthGlobalFilter — 鉴权过滤器

```java
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of(
        "/api/auth/login", "/api/auth/register", "/actuator/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 从 Header 取 Token
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.isBlank(token)) {
            return unauthorized(exchange);
        }

        // Sa-Token 校验
        try {
            StpUtil.checkLogin();
            // 透传 userId 到下游
            long userId = StpUtil.getLoginIdAsLong();
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (NotLoginException e) {
            return unauthorized(exchange);
        }
    }

    @Override
    public int getOrder() { return -100; }
}
```

#### 4.1.2 RateLimitFilter — 限流（Redis 令牌桶）

```java
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    // 每用户每分钟 60 次请求
    private static final int MAX_TOKENS = 60;
    private static final long REFILL_INTERVAL_MS = 60_000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId == null) return chain.filter(exchange);

        String key = "rate_limit:" + userId;
        return checkRateLimit(key).flatMap(allowed -> {
            if (!allowed) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return writeBody(exchange, "对不起，系统繁忙，请稍后重试");
            }
            return chain.filter(exchange);
        });
    }

    private Mono<Boolean> checkRateLimit(String key) {
        // Lua 脚本实现原子令牌桶
        // ...
    }

    @Override
    public int getOrder() { return -90; }
}
```

#### 4.1.3 RouteConfig — 路由配置

```java
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // Agent 服务
            .route("agent-service", r -> r
                .path("/api/chat/**", "/api/conversation/**")
                .filters(f -> f
                    .filter(traceIdFilter())
                    .retry(retry -> retry.setRetries(2))
                )
                .uri("lb://agent-service"))
            // 未来扩展
            .route("document-parse", r -> r
                .path("/api/document/**")
                .uri("lb://document-parse-service"))
            .route("ppt-rend", r -> r
                .path("/api/ppt/**")
                .uri("lb://ppt-rend-service"))
            .build();
    }
}
```

#### 4.1.4 AuthController — 登录/注册（Gateway 直接处理）

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok();
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        StpUtil.logout();
        return R.ok();
    }
}
```

#### 4.1.5 AuthService — 认证逻辑

```java
@Service
public class AuthService {

    private final UserMapper userMapper;

    /** 注册 */
    @Transactional
    public void register(RegisterRequest request) {
        // 1. 检查用户名是否已存在
        if (userMapper.selectByUsername(request.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 2. BCrypt 加密密码
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setNickname(request.getNickname());
        user.setStatus(1);
        userMapper.insert(user);
    }

    /** 登录：校验密码 → Sa-Token 签发 → Redis 存储 */
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        // Sa-Token 登录，Token 存 Redis，有效期 7 天
        StpUtil.login(user.getId());
        return new LoginResponse(
            StpUtil.getTokenValue(),
            user.getId(),
            user.getUsername(),
            user.getNickname()
        );
    }
}
```

#### 4.1.6 UserMapper — Gateway 直连 MySQL

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);
}
```

### 4.2 Agent 模块（ChatAgent 日常聊天）

#### 4.2.1 ChatController — 对话接口

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式对话（SSE）
     * POST /api/chat/stream
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamResponse>> chatStream(
            @RequestBody ChatRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return chatService.streamChat(userId, request);
    }
}
```

#### 4.2.2 ChatService — 核心对话逻辑

```java
@Service
public class ChatService {

    private final ChatAgent chatAgent;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final RedisTemplate<String, String> redisTemplate;

    public Flux<ServerSentEvent<ChatStreamResponse>> streamChat(Long userId, ChatRequest request) {
        // 1. 获取或创建会话
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = conversationService.createConversation(userId, request.getContent());
        }

        // 2. 保存用户消息
        messageService.saveMessage(conversationId, userId, "user", request.getContent());

        // 3. 从 Redis 获取近期上下文（最近 20 条）
        List<MessageDTO> context = messageService.getRecentMessages(conversationId, 20);

        // 4. 调用 ChatAgent 流式对话
        return chatAgent.stream(context, request)
            .doOnNext(event -> {
                if ("done".equals(event.getType())) {
                    // 保存助手完整回复
                    messageService.saveMessage(conversationId, userId, "assistant",
                        event.getFullContent(), event.getMeta());
                    // 更新会话标题（首条消息时）
                    conversationService.updateTitleIfNeeded(conversationId, request.getContent());
                }
            })
            .map(event -> ServerSentEvent.<ChatStreamResponse>builder()
                .event(event.getType())
                .data(event)
                .build());
    }
}
```

#### 4.2.3 ChatAgent — DashScope 流式调用

```java
@Component
public class ChatAgent {

    private final ChatModel chatModel;  // Spring AI Alibaba DashScope ChatModel

    public Flux<ChatStreamResponse> stream(List<MessageDTO> history, ChatRequest request) {
        // 构建 Spring AI Prompt
        List<Message> messages = buildMessages(history);

        ChatOptions options = ChatOptions.builder()
            .model("qwen-plus")
            .stream(true)
            .build();

        // 深度思考模式
        if (request.isDeepThink()) {
            // 通过 DashScope 特有参数启用 thinking
            options = DashScopeChatOptions.builder()
                .withModel("qwen-plus")
                .withEnableThinking(true)
                .withThinkingIntensity("medium")
                .build();
        }

        Prompt prompt = new Prompt(messages, options);

        return chatModel.stream(prompt)
            .map(response -> {
                ChatStreamResponse result = new ChatStreamResponse();
                String content = response.getResult().getOutput().getContent();

                // 判断是否为思考内容
                if (isThinkingContent(response)) {
                    result.setType("thinking");
                    result.setContent(content);
                } else {
                    result.setType("answer");
                    result.setContent(content);
                }
                return result;
            })
            .concatWith(Flux.just(ChatStreamResponse.done()));
    }
}
```

#### 4.2.4 ConversationController — 会话管理

```java
@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    /** 获取用户会话列表 */
    @GetMapping("/list")
    public R<List<ConversationDTO>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(conversationService.listConversations(userId, lastId, size));
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestHeader("X-User-Id") Long userId) {
        conversationService.deleteConversation(id, userId);
        return R.ok();
    }

    /** 重命名会话 */
    @PutMapping("/{id}/title")
    public R<Void> rename(@PathVariable Long id,
                          @RequestHeader("X-User-Id") Long userId,
                          @RequestBody Map<String, String> body) {
        conversationService.rename(id, userId, body.get("title"));
        return R.ok();
    }
}
```

### 4.3 数据结构定义

#### 请求/响应 DTO

```java
// 对话请求
@Data
public class ChatRequest {
    private String conversationId;       // 为空则自动创建
    private String content;              // 用户输入
    private boolean deepThink;           // 深度思考开关
    private boolean webSearch;           // 联网搜索开关
    private boolean pptMode;             // PPT 生成开关
    private boolean knowledgeMode;       // 知识库开关
    private String knowledgeBaseId;      // 知识库模式下指定库
}

// SSE 流式响应
@Data
public class ChatStreamResponse {
    private String type;       // thinking / answer / citation / done / error
    private String content;    // 内容片段
    private Object meta;       // 引用/工具调用等元数据
    private String messageId;  // 消息 ID

    public static ChatStreamResponse done() {
        ChatStreamResponse r = new ChatStreamResponse();
        r.setType("done");
        return r;
    }
}

// 统一响应体
@Data
public class R<T> {
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }
}
```

#### 实体类

```java
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String mode;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long userId;
    private String role;
    private String content;
    private String meta;       // JSON 字符串
    private Integer tokenCount;
    private Integer seqNo;
    private LocalDateTime createdAt;
}
```

---

## 五、微服务通信流程

### 5.1 日常聊天完整链路

```
┌──────────┐   HTTPS    ┌────────┐   HTTP    ┌──────────┐   HTTP    ┌──────────┐
│  Vue 前端 │ ─────────▶│ Nginx  │ ────────▶│ Gateway  │ ────────▶│  Agent   │
│          │  POST /api │  :80   │  /api/   │  :8080   │  lb://   │  :8081   │
│          │  /chat/    │        │  chat/*  │ 鉴权+限流 │  agent   │ ChatAgent│
│          │  stream    │        │          │          │          │          │
└──────────┘            └────────┘          └──────────┘          └──────────┘
     ▲                      │                    │                      │
     │                 SSE 透传             Redis 校验            DashScope API
     │               (proxy_buffering off)   Token               (流式调用)
     │                      │                    │                      │
     └── text/event-stream ─┴────────────────────┴──────────────────────┘

内部调用：
  Nginx  → Gateway：反向代理，SSE 不缓冲，IP 限流
  Gateway → Redis：限流令牌桶 + Sa-Token 校验
  Gateway → Nacos：服务发现（lb://agent-service）
  Agent → MySQL：保存消息（MyBatis-Plus）
  Agent → Redis：缓存会话上下文
  Agent → DashScope：LLM 流式对话（Spring AI Alibaba）
```

### 5.2 登录认证流程

```
前端                    Nginx                  Gateway                MySQL / Redis
 │                        │                      │                        │
 │── POST /api/auth/login ──▶│                  │                        │
 │                        │── proxy_pass ──────▶│                        │
 │                        │                      │── 查用户（MySQL）────▶│
 │                        │                      │◀── 返回 user ─────────│
 │                        │                      │── BCrypt 校验密码     │
 │                        │                      │── StpUtil.login() ──▶│
 │                        │                      │   (Token 写入 Redis)  │
 │◀── { token, userId } ──│◀────────────────────│                        │
 │                        │                      │                        │
 │── 后续请求 ────────────▶│                      │                        │
 │   Header: Authorization│                      │                        │
 │                        │── proxy_pass ──────▶│                        │
 │                        │                      │── Sa-Token 校验 ─────▶│
 │                        │                      │   (查 Redis)           │
 │                        │                      │── 透传 X-User-Id ───▶│
 │                        │                      │   路由到 agent-service │
 │                        │                      │                        │

说明：
  - 登录/注册/登出由 Gateway 直接处理，不转发到 Agent
  - Gateway 直连 MySQL（user 表）+ Redis（Token 会话）
  - 白名单路径：/api/auth/login, /api/auth/register, /actuator/health
  - 其余路径必须携带有效 Token，Gateway 校验后透传 X-User-Id 给下游
```

### 5.3 SSE 流式通信协议

前端通过 `fetch-event-source` 发起 POST 请求，服务端返回 `text/event-stream`：

```
event: thinking
data: {"type":"thinking","content":"让我分析一下这个问题..."}

event: answer
data: {"type":"answer","content":"你好！"}

event: answer
data: {"type":"answer","content":"这是一个很好的问题。"}

event: done
data: {"type":"done","content":"","messageId":"123456"}
```

---

## 六、前端关键设计

### 6.1 SSE 封装（src/utils/sse.js）

```javascript
import { fetchEventSource } from '@microsoft/fetch-event-source';

export function chatStream(params, { onThinking, onAnswer, onDone, onError }) {
    const ctrl = new AbortController();

    fetchEventSource('/api/chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify(params),
        signal: ctrl.signal,
        onmessage(ev) {
            const data = JSON.parse(ev.data);
            switch (ev.event) {
                case 'thinking': onThinking?.(data); break;
                case 'answer':   onAnswer?.(data);   break;
                case 'done':     onDone?.(data);      break;
                case 'error':    onError?.(data);     break;
            }
        },
        onerror(err) { onError?.(err); }
    });

    return ctrl;  // 返回 AbortController 用于取消
}
```

### 6.2 Pinia Store 核心结构

```javascript
// store/modules/chat.js
export const useChatStore = defineStore('chat', {
    state: () => ({
        currentConversationId: null,
        messages: [],           // 当前会话消息列表
        streaming: false,       // 是否正在流式输出
        streamingContent: '',   // 流式累积内容
        thinkingContent: '',    // 思考内容
    }),
    actions: {
        async sendMessage(content, options) {
            this.streaming = true;
            this.streamingContent = '';
            // 添加用户消息到列表
            this.messages.push({ role: 'user', content });

            const ctrl = chatStream(
                { conversationId: this.currentConversationId, content, ...options },
                {
                    onThinking: (data) => { this.thinkingContent += data.content; },
                    onAnswer: (data) => { this.streamingContent += data.content; },
                    onDone: (data) => {
                        this.messages.push({ role: 'assistant', content: this.streamingContent });
                        this.currentConversationId = data.conversationId || this.currentConversationId;
                        this.streaming = false;
                        this.streamingContent = '';
                        this.thinkingContent = '';
                    }
                }
            );
            this._abortCtrl = ctrl;
        },
        stopStreaming() {
            this._abortCtrl?.abort();
            this.streaming = false;
        }
    }
});
```

### 6.3 主聊天页组件结构（ChatView.vue）

```
┌─────────────────────────────────────────────────────────┐
│ ChatView.vue                                            │
│ ┌──────────┐ ┌────────────────────────────────────────┐ │
│ │ Sidebar  │ │ Header（模式开关：思考/搜索/PPT/知识库）│ │
│ │          │ ├────────────────────────────────────────┤ │
│ │ 会话列表  │ │ MessageList                            │ │
│ │ +新建对话 │ │  ├─ WelcomePage（无消息时）            │ │
│ │          │ │  ├─ MessageItem × N                    │ │
│ │ 历史会话  │ │  │   ├─ ThinkingBlock（可折叠）        │ │
│ │ (滚动加载)│ │  │   └─ Markdown 渲染内容              │ │
│ │          │ │  └─ StreamingMessage（正在输出）        │ │
│ │          │ ├────────────────────────────────────────┤ │
│ │          │ │ ChatInput（输入框 + 发送/停止按钮）     │ │
│ └──────────┘ └────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 七、Gateway 模块详细设计

### 7.1 鉴权（Sa-Token 集成）

```yaml
# gateway application.yml
sa-token:
    token-name: Authorization
    token-prefix: Bearer
    timeout: 604800        # 7 天（秒）
    is-concurrent: true    # 允许多端登录
    is-share: true
    token-style: uuid
```

Sa-Token 通过 Redis 存储 Token 会话数据，gateway 与 agent 共享同一 Redis 实例，实现 Token 校验一致性。

> **注意**：由于登录/注册逻辑在 Gateway 直接处理，Gateway 需要同时配置 MySQL 数据源（查询 user 表）和 Redis（Sa-Token 会话存储）。详见 §11.1 Gateway Nacos 配置。

### 7.2 限流策略

| 层级 | 维度 | 限制 | 实现 |
|---|---|---|---|
| Nginx | IP | 100 req/s | `limit_req zone=ip_limit` |
| Gateway | 用户 | 60 req/min | Redis 令牌桶（Lua 脚本） |
| Gateway | 对话接口 | 10 req/min/user | 针对 `/api/chat/stream` 单独限流 |
| Agent | DashScope | 按模型 RPM 80% | 本地令牌桶，超限切 qwen-turbo |

### 7.3 路由规则

| 路径 | 目标 | 说明 |
|---|---|---|
| `/api/auth/**` | **Gateway 自身处理** | 登录/注册/登出，白名单无需 Token |
| `/api/chat/stream` | agent-service | 对话 SSE 流式（不重试、不缓冲） |
| `/api/chat/**` | agent-service | 其他对话接口 |
| `/api/conversation/**` | agent-service | 会话管理 |
| `/api/document/**` | document-parse-service | 文档（预留） |
| `/api/ppt/**` | ppt-rend-service | PPT（预留） |
| `/actuator/health` | **Gateway 自身处理** | 健康检查，白名单 |

### 7.4 SSE 透传配置

```yaml
# gateway 需确保 SSE 不缓冲
spring:
  cloud:
    gateway:
      httpclient:
        response-timeout: 300s
      routes:
        - id: agent-sse
          uri: lb://agent-service
          predicates:
            - Path=/api/chat/stream
          filters:
            - name: Retry
              args:
                retries: 0    # SSE 不重试
```

---

## 八、Nginx 配置

> 版本：Nginx 1.29
> 职责：前端静态资源托管、API 反向代理到 Gateway、SSE 透传、IP 维度限流、TLS 终止

### 8.1 主配置文件（nginx.conf）

```nginx
# /etc/nginx/nginx.conf

worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid       /var/log/nginx/nginx.pid;

events {
    worker_connections 2048;
    use epoll;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for" '
                    'rt=$request_time';

    access_log /var/log/nginx/access.log main;

    sendfile    on;
    tcp_nopush  on;
    tcp_nodelay on;

    keepalive_timeout 65;

    # ============================================================
    # Gzip 压缩
    # ============================================================
    gzip on;
    gzip_min_length 1k;
    gzip_comp_level 4;
    gzip_types text/plain application/json application/javascript
               text/css application/xml text/event-stream;
    gzip_vary on;

    # ============================================================
    # IP 维度限流（第一层限流）
    # ============================================================
    # 每个 IP 每秒 100 个请求，突发允许 200
    limit_req_zone $binary_remote_addr zone=ip_limit:10m rate=100r/s;
    # 每个 IP 最大 100 个并发连接
    limit_conn_zone $binary_remote_addr zone=ip_conn:10m;

    # ============================================================
    # 上游 Gateway 服务
    # ============================================================
    upstream gateway_backend {
        server 127.0.0.1:8080;   # Gateway 端口
        keepalive 64;             # 长连接池
    }

    # ============================================================
    # 主 Server：前端 + API
    # ============================================================
    server {
        listen 80;
        server_name localhost;

        # ----------------------------------------------------------
        # 前端静态资源（Vue 打包产物）
        # ----------------------------------------------------------
        location / {
            root /usr/share/nginx/html;
            index index.html;
            try_files $uri $uri/ /index.html;   # SPA 路由回退

            # 静态资源缓存
            location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
                expires 7d;
                add_header Cache-Control "public, immutable";
            }
        }

        # ----------------------------------------------------------
        # API 反向代理 → Gateway
        # ----------------------------------------------------------
        location /api/ {
            # IP 限流
            limit_req zone=ip_limit burst=200 nodelay;
            limit_conn ip_conn 100;
            limit_req_status 429;
            limit_conn_status 429;

            proxy_pass http://gateway_backend;

            # 标准代理头
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            # 超时设置
            proxy_connect_timeout 10s;
            proxy_send_timeout    60s;
            proxy_read_timeout    60s;

            # 关闭缓冲，确保实时响应
            proxy_buffering off;
        }

        # ----------------------------------------------------------
        # SSE 专用 location（对话流式接口）
        # ----------------------------------------------------------
        location /api/chat/stream {
            limit_req zone=ip_limit burst=50 nodelay;
            limit_conn ip_conn 50;

            proxy_pass http://gateway_backend;

            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            # ★ SSE 关键配置 ★
            proxy_buffering off;           # 禁止缓冲，实时推送
            proxy_cache off;               # 禁止缓存
            proxy_read_timeout 300s;       # SSE 长连接超时 5 分钟
            proxy_send_timeout 300s;
            chunked_transfer_encoding on;  # 支持分块传输

            # HTTP/1.1 长连接
            proxy_http_version 1.1;
            proxy_set_header Connection "";
        }

        # ----------------------------------------------------------
        # 健康检查（Nginx 自身）
        # ----------------------------------------------------------
        location /nginx-health {
            access_log off;
            return 200 "ok";
            add_header Content-Type text/plain;
        }

        # 错误页面
        error_page 429 /429.html;
        location = /429.html {
            internal;
            return 429 '{"code":429,"message":"请求过于频繁，请稍后重试"}';
            add_header Content-Type application/json;
        }
    }

    # ============================================================
    # （可选）TLS 配置
    # ============================================================
    # server {
    #     listen 443 ssl http2;
    #     server_name your-domain.com;
    #
    #     ssl_certificate     /etc/nginx/ssl/cert.pem;
    #     ssl_certificate_key /etc/nginx/ssl/key.pem;
    #     ssl_protocols       TLSv1.2 TLSv1.3;
    #     ssl_ciphers         HIGH:!aNULL:!MD5;
    #
    #     # ... 其余 location 同上 ...
    # }
    #
    # # HTTP → HTTPS 重定向
    # server {
    #     listen 80;
    #     server_name your-domain.com;
    #     return 301 https://$host$request_uri;
    # }
}
```

### 8.2 配置要点说明

| 配置项 | 值 | 说明 |
|---|---|---|
| `proxy_buffering off` | SSE location | 禁止 Nginx 缓冲 SSE 响应，确保逐 token 实时推送到前端 |
| `proxy_read_timeout 300s` | SSE location | SSE 长连接最大空闲 5 分钟，配合服务端 15s 心跳 |
| `proxy_http_version 1.1` | SSE location | 启用 HTTP/1.1 持久连接，支持 chunked 传输 |
| `limit_req_zone rate=100r/s` | 全局 | IP 维度第一层限流，burst=200 允许短时突发 |
| `try_files ... /index.html` | `/` location | Vue SPA 路由回退，所有前端路由交由 Vue Router 处理 |
| `upstream keepalive 64` | 全局 | Nginx → Gateway 长连接池，减少 TCP 握手开销 |
| `chunked_transfer_encoding on` | SSE location | 确保分块传输编码开启 |

### 8.3 请求转发路径总览

```
前端请求                        Nginx location              目标
─────────────────────────────────────────────────────────────────────
GET /                           location /                  Vue 静态文件
GET /assets/xxx.js              location /                  Vue 静态文件（缓存 7d）
POST /api/auth/login            location /api/              → Gateway :8080
POST /api/auth/register         location /api/              → Gateway :8080
POST /api/chat/stream           location /api/chat/stream   → Gateway :8080（SSE 专用）
GET  /api/conversation/list     location /api/              → Gateway :8080
GET  /actuator/health           location /api/              → Gateway :8080
```

---

## 九、父 POM 依赖管理

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.aiassistant</groupId>
    <artifactId>aiassistant-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>aiassistant-common</module>
        <module>aiassistant-gateway</module>
        <module>aiassistant-agent</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.3.4</spring-boot.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.2</spring-cloud-alibaba.version>
        <spring-ai.version>1.0.0</spring-ai.version>
        <spring-ai-alibaba.version>1.0.0.2</spring-ai-alibaba.version>
        <sa-token.version>1.37.0</sa-token.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 十、开发排期（建议）

| 阶段 | 内容 | 预估工时 |
|---|---|---|
| **P0 基础设施** | 父 POM 搭建、common 模块、Docker Compose（MySQL/Redis/Nacos） | 2 天 |
| **P1 数据库** | 建表 DDL、MyBatis-Plus 实体/Mapper、基础 CRUD | 1 天 |
| **P2 认证** | Gateway 内注册/登录、Sa-Token 集成、Gateway 鉴权过滤器、user 表 CRUD | 2 天 |
| **P3 Gateway** | 路由配置、限流、跨域、SSE 透传、traceId | 2 天 |
| **P4 ChatAgent** | DashScope 接入、流式对话、消息持久化、上下文管理 | 3 天 |
| **P5 前端基础** | 项目搭建、登录/注册页、路由/状态管理 | 2 天 |
| **P6 前端聊天** | SSE 对接、消息流式渲染、Markdown、侧边栏 | 3 天 |
| **P7 联调测试** | 端到端联调、SSE 稳定性、限流验证 | 2 天 |
| **合计** | | **约 17 天** |

---

## 十一、Nacos 配置示例

### 11.1 Gateway 配置（aiassistant-gateway-dev.yml）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/ai_assistant?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: redis
      port: 6379
  cloud:
    gateway:
      httpclient:
        response-timeout: 300s
      routes:
        - id: agent-chat-sse
          uri: lb://agent-service
          predicates:
            - Path=/api/chat/stream
          filters:
            - name: Retry
              args:
                retries: 0
        - id: agent-service
          uri: lb://agent-service
          predicates:
            - Path=/api/chat/**,/api/conversation/**

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

sa-token:
  token-name: Authorization
  token-prefix: Bearer
  timeout: 604800
  is-concurrent: true
  is-share: true
  token-style: uuid
```

### 11.2 Agent 配置（aiassistant-agent-dev.yml）

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/ai_assistant?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: redis
      port: 6379
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```
