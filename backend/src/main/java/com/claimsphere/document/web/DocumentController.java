package com.claimsphere.document.web;

import com.claimsphere.document.domain.DocumentType;
import com.claimsphere.document.dto.DocumentResponse;
import com.claimsphere.document.service.DocumentService;
import com.claimsphere.security.CurrentUserProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUserProvider currentUserProvider;

    public DocumentController(DocumentService documentService, CurrentUserProvider currentUserProvider) {
        this.documentService = documentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLAIMANT', 'CLAIMS_OFFICER', 'MANAGER')")
    public DocumentResponse upload(@PathVariable UUID claimId, @RequestParam("file") MultipartFile file,
                                   @RequestParam DocumentType documentType) {
        return documentService.upload(claimId, documentType, file, currentUserProvider.get());
    }

    @GetMapping
    public List<DocumentResponse> list(@PathVariable UUID claimId) {
        return documentService.list(claimId, currentUserProvider.get());
    }

    /** Always served as an attachment so the browser never renders uploaded content inline in the app origin. */
    @GetMapping("/{documentId}/content")
    public ResponseEntity<Resource> download(@PathVariable UUID claimId, @PathVariable UUID documentId) {
        DocumentService.DocumentContent content = documentService.download(claimId, documentId,
                currentUserProvider.get());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(content.resource());
    }
}
