package com.claimsphere.document.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.service.ClaimAccessPolicy;
import com.claimsphere.claim.service.ClaimLookup;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.ConflictException;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.document.domain.ClaimDocument;
import com.claimsphere.document.domain.DocumentType;
import com.claimsphere.document.dto.DocumentResponse;
import com.claimsphere.document.repository.ClaimDocumentRepository;
import com.claimsphere.document.storage.FileStorage;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Claim document upload and retrieval. Access follows the claim's visibility rules; claimants may only attach
 * documents to their own claims, and nothing can be attached once a claim is closed.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_FILE_NAME = 255;

    private final ClaimDocumentRepository repository;
    private final FileStorage storage;
    private final ClaimLookup claimLookup;
    private final ClaimAccessPolicy accessPolicy;
    private final UserService userService;
    private final AuditService auditService;
    private final Clock clock;
    private final long maxSizeBytes;

    public DocumentService(ClaimDocumentRepository repository, FileStorage storage, ClaimLookup claimLookup,
                           ClaimAccessPolicy accessPolicy, UserService userService, AuditService auditService,
                           Clock clock, @Value("${app.storage.max-file-size-bytes}") long maxSizeBytes) {
        this.repository = repository;
        this.storage = storage;
        this.claimLookup = claimLookup;
        this.accessPolicy = accessPolicy;
        this.userService = userService;
        this.auditService = auditService;
        this.clock = clock;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Transactional
    public DocumentResponse upload(UUID claimId, DocumentType documentType, MultipartFile file, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        if (user.is(Role.CLAIMANT)) {
            accessPolicy.requireOwner(claim, user);
        }
        if (claim.getStatus().isTerminal()) {
            throw new ConflictException("CLAIM_CLOSED", "Documents cannot be added to a closed claim");
        }
        if (file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_DOCUMENT", "The uploaded file is empty");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessRuleException("DOCUMENT_TOO_LARGE",
                    "Documents must not exceed %d MB".formatted(maxSizeBytes / (1024 * 1024)));
        }
        DocumentFormat format = DocumentFormat.require(readHeader(file));

        String storageKey = "claims/%s/%s".formatted(claimId, UUID.randomUUID());
        String checksum = storeAndHash(storageKey, file);
        deleteOnRollback(storageKey);

        ClaimDocument document = repository.save(new ClaimDocument(claim, userService.getReference(user.id()),
                documentType, sanitiseFileName(file.getOriginalFilename()), format.contentType(), file.getSize(),
                storageKey, checksum, clock.instant()));
        auditService.record(user.id(), AuditAction.DOCUMENT_UPLOADED, "CLAIM", claimId,
                "%s %s (%d bytes)".formatted(documentType, document.getFileName(), document.getSizeBytes()));
        log.info("Document uploaded claimId={} documentId={} type={} bytes={}",
                claimId, document.getId(), documentType, document.getSizeBytes());
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID claimId, CurrentUser user) {
        claimLookup.loadVisible(claimId, user);
        return repository.findByClaimIdOrderByCreatedAtAsc(claimId).stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentContent download(UUID claimId, UUID documentId, CurrentUser user) {
        claimLookup.loadVisible(claimId, user);
        ClaimDocument document = repository.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        try {
            Resource resource = storage.load(document.getStorageKey());
            return new DocumentContent(document.getFileName(), document.getContentType(), document.getSizeBytes(),
                    resource);
        } catch (IOException e) {
            log.error("Stored content missing documentId={} key={}", documentId, document.getStorageKey(), e);
            throw new ResourceNotFoundException("Document content", documentId);
        }
    }

    /** Binary content plus the metadata needed to serve it. */
    public record DocumentContent(String fileName, String contentType, long sizeBytes, Resource resource) {
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(DocumentFormat.SIGNATURE_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }

    private String storeAndHash(String storageKey, MultipartFile file) {
        try (DigestInputStream in = new DigestInputStream(file.getInputStream(), MessageDigest.getInstance("SHA-256"))) {
            storage.store(storageKey, in);
            return HexFormat.of().formatHex(in.getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store uploaded file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Keeps storage consistent with the database: a rolled-back upload leaves no orphaned file behind. */
    private void deleteOnRollback(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(storageKey);
                    } catch (IOException e) {
                        log.warn("Could not remove orphaned upload key={}", storageKey, e);
                    }
                }
            }
        });
    }

    static String sanitiseFileName(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "_").trim();
        if (name.isEmpty()) {
            return "document";
        }
        return name.length() <= MAX_FILE_NAME ? name : name.substring(name.length() - MAX_FILE_NAME);
    }
}
