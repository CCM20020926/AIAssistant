-- ============================================================
-- AI Assistant 数据库初始化脚本
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ai_assistant`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `ai_assistant`;

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
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
CREATE TABLE IF NOT EXISTS `conversation` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT UNSIGNED  NOT NULL COMMENT '所属用户',
    `title`             VARCHAR(256)     NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `mode`              VARCHAR(32)      NOT NULL DEFAULT 'chat' COMMENT 'chat/web_search/ppt/knowledge',
    `knowledge_base_id` BIGINT UNSIGNED  DEFAULT NULL COMMENT '关联知识库ID',
    `status`            TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-活跃 0-归档 2-删除',
    `created_at`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_updated` (`user_id`, `status`, `updated_at` DESC),
    KEY `idx_user_created` (`user_id`, `created_at` DESC),
    KEY `idx_knowledge_base` (`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ============================================================
-- 消息表（数据量最大，重点优化索引）
-- ============================================================
CREATE TABLE IF NOT EXISTS `message` (
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
    KEY `idx_conv_seq` (`conversation_id`, `seq_no`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_conv_created` (`conversation_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ============================================================
-- 知识库表
-- ============================================================
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

-- ============================================================
-- 文档表
-- ============================================================
CREATE TABLE IF NOT EXISTS `document` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT UNSIGNED  NOT NULL,
    `knowledge_base_id` BIGINT UNSIGNED  NOT NULL COMMENT '所属知识库',
    `name`              VARCHAR(256)     NOT NULL COMMENT '原始文件名',
    `file_type`         VARCHAR(16)      NOT NULL COMMENT 'pdf/docx/txt/md',
    `file_size`         BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
    `minio_path`        VARCHAR(512)     NOT NULL COMMENT 'MinIO 对象路径',
    `parse_status`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-待解析 1-解析中 2-已完成 3-失败',
    `parse_error`       TEXT             DEFAULT NULL COMMENT '解析错误信息',
    `chunk_count`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '分块数量',
    `created_at`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_kb_status` (`knowledge_base_id`, `parse_status`),
    KEY `idx_user` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- ============================================================
-- 文档分块表
-- ============================================================
CREATE TABLE IF NOT EXISTS `document_chunk` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `document_id`       BIGINT UNSIGNED  NOT NULL COMMENT '所属文档',
    `knowledge_base_id` BIGINT UNSIGNED  NOT NULL COMMENT '所属知识库',
    `chunk_index`       INT UNSIGNED     NOT NULL COMMENT '分块序号',
    `content_preview`   VARCHAR(500)     DEFAULT NULL COMMENT '内容预览(前500字符)',
    `minio_path`        VARCHAR(512)     NOT NULL COMMENT 'MinIO 分块文件路径',
    `token_count`       INT UNSIGNED     DEFAULT 0 COMMENT 'token 数量',
    `created_at`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_document` (`document_id`, `chunk_index`),
    KEY `idx_kb` (`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档分块表';

-- ============================================================
-- PPT 任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ppt_task` (
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
