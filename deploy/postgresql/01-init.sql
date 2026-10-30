-- ============================================================
-- AI Assistant 向量数据库初始化脚本
-- PostgreSQL 15+ with pgvector
-- ============================================================

CREATE DATABASE ai_assistant_vectors;

\c ai_assistant_vectors;

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 文档分块向量表
-- ============================================================
CREATE TABLE IF NOT EXISTS chunk_vector (
    id                BIGSERIAL       PRIMARY KEY,
    document_id       BIGINT          NOT NULL,
    knowledge_base_id BIGINT          NOT NULL,
    chunk_index       INT             NOT NULL,
    content           TEXT            NOT NULL,
    minio_path        VARCHAR(512)    NOT NULL,
    embedding         vector(1024)    NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chunk_vector_doc_index UNIQUE (document_id, chunk_index)
);

-- HNSW 向量索引（余弦相似度）
CREATE INDEX idx_chunk_vector_embedding
    ON chunk_vector
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

CREATE INDEX idx_chunk_vector_kb
    ON chunk_vector (knowledge_base_id);
