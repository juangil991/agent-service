CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_search;

CREATE TABLE IF NOT EXISTS protocol_document (
    document_id TEXT PRIMARY KEY,
    protocol_id TEXT NOT NULL,
    title TEXT NOT NULL,
    version TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS protocol_chunk (
    chunk_id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL REFERENCES protocol_document(document_id) ON DELETE CASCADE,
    protocol_id TEXT NOT NULL,
    section TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(1024) NOT NULL,
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS protocol_chunk_search_idx
    ON protocol_chunk
    USING paradedb (chunk_id, protocol_id, section, content, embedding vector_cosine_ops);
