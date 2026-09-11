CREATE TABLE documents
(
    id                    BIGSERIAL PRIMARY KEY,
    public_id             VARCHAR(36)              NOT NULL,
    original_filename     VARCHAR(255)             NOT NULL,
    content_type          VARCHAR(100)             NOT NULL,
    size_bytes            BIGINT                   NOT NULL,
    checksum              VARCHAR(64)              NOT NULL,
    storage_key           VARCHAR(500)             NOT NULL,
    status                VARCHAR(20)              NOT NULL,
    retry_count           INTEGER                  NOT NULL DEFAULT 0,
    failure_reason        VARCHAR(1000),
    uploaded_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    processing_started_at TIMESTAMP WITH TIME ZONE,
    finished_at           TIMESTAMP WITH TIME ZONE,

    -- OcrResult (@Embeddable)
    extracted_text        TEXT,
    confidence            DOUBLE PRECISION,
    ocr_engine            VARCHAR(50),
    ocr_language          VARCHAR(50),
    page_count            INTEGER,
    duration_millis       BIGINT,
    processed_at          TIMESTAMP WITH TIME ZONE,

    version               BIGINT                   NOT NULL DEFAULT 0,

    CONSTRAINT uk_documents_public_id UNIQUE (public_id)
);

-- 스케줄러가 "오래 기다린 PENDING 문서"를 집을 때 쓰는 인덱스
CREATE INDEX idx_documents_status_uploaded_at ON documents (status, uploaded_at);

-- 중복 업로드 판정용
CREATE INDEX idx_documents_checksum ON documents (checksum);
