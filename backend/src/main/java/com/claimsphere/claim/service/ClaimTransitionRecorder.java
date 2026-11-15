package com.claimsphere.claim.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.domain.ClaimStatusHistory;
import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.claim.event.ClaimEventPublisher;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.claim.repository.ClaimStatusHistoryRepository;
import com.claimsphere.common.web.CorrelationId;
import com.claimsphere.user.domain.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/**
 * The single place where a claim change is made durable, all within the caller's transaction:
 * persist the claim (surfacing optimistic-lock conflicts), append the status history when the status changed,
 * write the audit record and enqueue the domain event in the outbox.
 */
@Component
public class ClaimTransitionRecorder {

    static final String ENTITY_TYPE = "CLAIM";

    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final ClaimEventPublisher eventPublisher;
    private final AuditService auditService;
    private final Clock clock;

    public ClaimTransitionRecorder(ClaimRepository claimRepository, ClaimStatusHistoryRepository historyRepository,
                                   ClaimEventPublisher eventPublisher, AuditService auditService, Clock clock) {
        this.claimRepository = claimRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    public void record(Claim claim, ClaimStatus from, User actor, String comment, ClaimEventType type) {
        claim.markClosed(clock.instant());
        claimRepository.saveAndFlush(claim);
        String note = blankToNull(comment);
        if (from != claim.getStatus()) {
            historyRepository.save(new ClaimStatusHistory(claim, from, claim.getStatus(), actor, note,
                    clock.instant()));
        }
        auditService.record(actor.getId(), AuditAction.valueOf(type.name()), ENTITY_TYPE, claim.getId(),
                "%s -> %s%s".formatted(from, claim.getStatus(), note == null ? "" : ": " + note));
        eventPublisher.publish(toEvent(claim, from, actor.getId(), type));
    }

    private ClaimEvent toEvent(Claim claim, ClaimStatus from, UUID actorId, ClaimEventType type) {
        User officer = claim.getAssignedOfficer();
        return new ClaimEvent(UUID.randomUUID(), type, ClaimEvent.CURRENT_SCHEMA_VERSION, clock.instant(),
                CorrelationId.current(), claim.getId(), claim.getClaimNumber(), claim.getClaimant().getId(),
                officer == null ? null : officer.getId(), from == null ? null : from.name(),
                claim.getStatus().name(), eventAmount(claim, type), claim.getCurrency().name(), actorId);
    }

    private static BigDecimal eventAmount(Claim claim, ClaimEventType type) {
        return switch (type) {
            case CLAIM_SETTLED -> claim.getSettledAmount();
            case CLAIM_APPROVED -> claim.getApprovedAmount();
            default -> claim.getEstimatedLoss();
        };
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
