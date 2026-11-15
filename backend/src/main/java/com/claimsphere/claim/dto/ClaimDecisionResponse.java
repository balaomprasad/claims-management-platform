package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimDecision;
import com.claimsphere.claim.domain.DecisionOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimDecisionResponse(UUID id, DecisionOutcome outcome, BigDecimal amount, String reason,
                                    UUID decidedById, String decidedByName, Instant decidedAt) {

    public static ClaimDecisionResponse from(ClaimDecision decision) {
        return new ClaimDecisionResponse(decision.getId(), decision.getOutcome(), decision.getAmount(),
                decision.getReason(), decision.getDecidedBy().getId(), decision.getDecidedBy().getFullName(),
                decision.getDecidedAt());
    }
}
