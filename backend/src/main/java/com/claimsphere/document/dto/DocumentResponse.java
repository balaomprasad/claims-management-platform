package com.claimsphere.document.dto;

import com.claimsphere.document.domain.ClaimDocument;
import com.claimsphere.document.domain.DocumentType;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, UUID claimId, DocumentType documentType, String fileName,
                               String contentType, long sizeBytes, String checksumSha256, UUID uploadedById,
                               String uploadedByName, Instant createdAt) {

    public static DocumentResponse from(ClaimDocument document) {
        return new DocumentResponse(document.getId(), document.getClaim().getId(), document.getDocumentType(),
                document.getFileName(), document.getContentType(), document.getSizeBytes(),
                document.getChecksumSha256(), document.getUploadedBy().getId(),
                document.getUploadedBy().getFullName(), document.getCreatedAt());
    }
}
