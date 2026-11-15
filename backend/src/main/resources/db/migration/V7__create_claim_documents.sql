-- V7: Claim document metadata. Binary content lives in the file-storage adapter, never in the database.
CREATE TABLE claim_documents (
    id              UUID                     PRIMARY KEY,
    claim_id        UUID                     NOT NULL,
    uploaded_by     UUID                     NOT NULL,
    document_type   VARCHAR(30)              NOT NULL,
    file_name       VARCHAR(255)             NOT NULL,
    content_type    VARCHAR(100)             NOT NULL,
    size_bytes      BIGINT                   NOT NULL,
    storage_key     VARCHAR(300)             NOT NULL,
    checksum_sha256 VARCHAR(64)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_documents_claim FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_documents_type CHECK (document_type IN
        ('PHOTO', 'INVOICE', 'REPAIR_ESTIMATE', 'MEDICAL_REPORT', 'POLICE_REPORT', 'RECEIPT', 'OTHER')),
    CONSTRAINT ck_documents_size CHECK (size_bytes > 0)
);
CREATE INDEX idx_documents_claim ON claim_documents (claim_id, created_at);
