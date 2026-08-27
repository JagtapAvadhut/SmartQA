-- SmartQA vector RAG (PostgreSQL + pgvector)
-- Embedding model (default): nomic-embed-text via Ollama → 768 dimensions
-- Distance metric: cosine (<=> / vector_cosine_ops)
-- Index: HNSW (good default for production; works from small datasets upward)
-- Do NOT change dimension without re-embedding all rows and updating this DDL.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS smartqa_knowledge (
    id                    UUID PRIMARY KEY,
    scope                 VARCHAR(32)  NOT NULL,
    scope_key             VARCHAR(256),
    content               TEXT         NOT NULL,
    content_type          VARCHAR(64)  NOT NULL,
    source                VARCHAR(64)  NOT NULL,
    source_run_id         UUID,
    source_test_case_id   UUID,
    metadata_json         TEXT,
    confidence            DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    success_count         INTEGER NOT NULL DEFAULT 0,
    failure_count         INTEGER NOT NULL DEFAULT 0,
    last_used_at          TIMESTAMP,
    embedding             vector(768) NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_smartqa_knowledge_scope CHECK (scope IN ('GLOBAL_GENERIC', 'APPLICATION', 'EXECUTION'))
);

CREATE INDEX IF NOT EXISTS idx_smartqa_knowledge_scope
    ON smartqa_knowledge (scope, scope_key);

CREATE INDEX IF NOT EXISTS idx_smartqa_knowledge_content_type
    ON smartqa_knowledge (content_type);

CREATE INDEX IF NOT EXISTS idx_smartqa_knowledge_updated_at
    ON smartqa_knowledge (updated_at DESC);

-- Cosine similarity ANN index (HNSW). Dataset expected to grow beyond sequential scan.
CREATE INDEX IF NOT EXISTS idx_smartqa_knowledge_embedding_hnsw
    ON smartqa_knowledge
    USING hnsw (embedding vector_cosine_ops);
