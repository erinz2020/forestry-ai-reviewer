-- pgvector is installed up front so future RAG migrations can add
-- embedding columns without a schema break.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id                   UUID         PRIMARY KEY,
    file_name            VARCHAR(255) NOT NULL,
    content_type         VARCHAR(255) NOT NULL,
    original_file_path   VARCHAR(1024) NOT NULL,
    extracted_text       TEXT,
    status               VARCHAR(32)  NOT NULL,
    uploaded_at          TIMESTAMP    NOT NULL
);

CREATE TABLE document_chunks (
    id              UUID    PRIMARY KEY,
    document_id     UUID    NOT NULL,
    chunk_index     INTEGER NOT NULL,
    content         TEXT    NOT NULL,
    start_offset    INTEGER NOT NULL,
    end_offset      INTEGER NOT NULL,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_chunks_document ON document_chunks (document_id, chunk_index);

CREATE TABLE findings (
    id                  UUID         PRIMARY KEY,
    document_id         UUID         NOT NULL,
    type                VARCHAR(64)  NOT NULL,
    severity            VARCHAR(32)  NOT NULL,
    location            VARCHAR(255),
    quote               TEXT,
    description         TEXT         NOT NULL,
    suggestion          TEXT,
    evidence            TEXT,
    confidence          DOUBLE PRECISION,
    source_references   TEXT,
    chunk_index         INTEGER,
    status              VARCHAR(32)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL
);

CREATE TABLE review_cases (
    id                          UUID         PRIMARY KEY,
    title                       VARCHAR(255),
    document_type               VARCHAR(255),
    section_type                VARCHAR(255),
    original_text               TEXT,
    reviewed_text               TEXT,
    reviewer_comment            TEXT,
    detected_change             TEXT,
    source_type                 VARCHAR(32)  NOT NULL,
    source_draft_file_name      VARCHAR(512),
    source_reviewed_file_name   VARCHAR(512) NOT NULL,
    draft_chunk_index           INTEGER,
    reviewed_chunk_index        INTEGER,
    comment_author              VARCHAR(255),
    comment_location            VARCHAR(255),
    created_at                  TIMESTAMP    NOT NULL
);
