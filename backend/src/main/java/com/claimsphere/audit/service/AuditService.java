package com.claimsphere.audit.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.domain.AuditLog;
import com.claimsphere.audit.dto.AuditLogResponse;
import com.claimsphere.audit.repository.AuditLogRepository;
import com.claimsphere.common.web.CorrelationId;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.security.CurrentUserProvider;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes audit records inside the caller's transaction, so an audit entry exists if and only if the change committed.
 */
@Service
public class AuditService {

    private static final int MAX_DETAILS = 2000;

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, CurrentUserProvider currentUserProvider, Clock clock) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /** Records an action performed by the authenticated user (or the system, when there is none). */
    @Transactional
    public void record(AuditAction action, String entityType, Object entityId, String details) {
        UUID actorId = currentUserProvider.find().map(CurrentUser::id).orElse(null);
        record(actorId, action, entityType, entityId, details);
    }

    @Transactional
    public void record(UUID actorId, AuditAction action, String entityType, Object entityId, String details) {
        repository.save(new AuditLog(actorId, action, entityType, String.valueOf(entityId), truncate(details),
                CorrelationId.current(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String entityType, String entityId, UUID actorId, AuditAction action,
                                         Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(AuditLogResponse::from);
    }

    private static String truncate(String details) {
        return details == null || details.length() <= MAX_DETAILS ? details : details.substring(0, MAX_DETAILS);
    }
}
