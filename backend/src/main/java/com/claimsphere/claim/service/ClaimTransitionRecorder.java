package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.domain.ClaimStatusHistory;
import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.claim.repository.ClaimStatusHistoryRepository;
import com.claimsphere.user.domain.User;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/**
 * The single place where a claim change is made durable: persist (surfacing optimistic-lock conflicts),
 * append to the status history when the status changed, and raise the domain event.
 */
@Component
public class ClaimTransitionRecorder {

    static final String CORRELATION_ID_KEY = "correlationId";

    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ClaimTransitionRecorder(ClaimRepository claimRepository, ClaimStatusHistoryRepository historyRepository,
                                   ApplicationEventPublisher eventPublisher, Clock clock) {
        this.claimRepository = claimRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public void record(Claim claim, ClaimStatus from, User actor, String comment, ClaimEventType type) {
        claim.markClosed(clock.instant());
        claimRepository.saveAndFlush(claim);
        if (from != claim.getStatus()) {
            historyRepository.save(new ClaimStatusHistory(claim, from, claim.getStatus(), actor,
                    blankToNull(comment), clock.instant()));
        }
        eventPublisher.publishEvent(toEvent(claim, from, actor.getId(), type));
    }

    private ClaimEvent toEvent(Claim claim, ClaimStatus from, UUID actorId, ClaimEventType type) {
        User officer = claim.getAssignedOfficer();
        return new ClaimEvent(UUID.randomUUID(), type, ClaimEvent.CURRENT_SCHEMA_VERSION, clock.instant(),
                MDC.get(CORRELATION_ID_KEY), claim.getId(), claim.getClaimNumber(), claim.getClaimant().getId(),
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
