CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE rag_documents (
    id           BIGSERIAL PRIMARY KEY,
    source_name  VARCHAR(255) NOT NULL UNIQUE,
    title        VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE rag_chunks (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT      NOT NULL REFERENCES rag_documents (id) ON DELETE CASCADE,
    position    INTEGER     NOT NULL,
    content     TEXT        NOT NULL,
    embedding   vector(768) NOT NULL
);

CREATE INDEX idx_rag_chunks_embedding ON rag_chunks USING hnsw (embedding vector_cosine_ops);
