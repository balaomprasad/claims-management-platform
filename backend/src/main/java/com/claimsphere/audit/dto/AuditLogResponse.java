package com.claimsphere.audit.dto;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.domain.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, UUID actorId, AuditAction action, String entityType, String entityId,
                               String details, String correlationId, Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorId(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getDetails(), log.getCorrelationId(), log.getCreatedAt());
    }
}
