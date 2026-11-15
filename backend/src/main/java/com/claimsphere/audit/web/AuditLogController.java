package com.claimsphere.audit.web;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.dto.AuditLogResponse;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> search(@RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) String entityId,
                                                 @RequestParam(required = false) UUID actorId,
                                                 @RequestParam(required = false) AuditAction action,
                                                 @PageableDefault(size = 50, sort = "createdAt",
                                                         direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(auditService.search(entityType, entityId, actorId, action, pageable));
    }
}
