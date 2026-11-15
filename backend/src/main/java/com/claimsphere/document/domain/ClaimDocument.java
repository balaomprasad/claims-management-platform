package com.claimsphere.document.domain;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a document attached to a claim. The content is held by the file-storage adapter under
 * {@code storageKey}; the file name is display-only and never used to build storage paths.
 */
@Entity
@Immutable
@Table(name = "claim_documents")
public class ClaimDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true, length = 300)
    private String storageKey;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClaimDocument() {
    }

    public ClaimDocument(Claim claim, User uploadedBy, DocumentType documentType, String fileName,
                         String contentType, long sizeBytes, String storageKey, String checksumSha256,
                         Instant createdAt) {
        this.claim = claim;
        this.uploadedBy = uploadedBy;
        this.documentType = documentType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.checksumSha256 = checksumSha256;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
