package com.claimsphere.claim.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle states and the single source of truth for permitted transitions.
 *
 * <pre>
 * SUBMITTED -> UNDER_REVIEW -> APPROVED -> SETTLED
 *     |             |-> REJECTED
 *     |-> WITHDRAWN <-|
 * </pre>
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    SETTLED,
    WITHDRAWN;

    public Set<ClaimStatus> allowedTransitions() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW, WITHDRAWN);
            case UNDER_REVIEW -> EnumSet.of(APPROVED, REJECTED, WITHDRAWN);
            case APPROVED -> EnumSet.of(SETTLED);
            case REJECTED, SETTLED, WITHDRAWN -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
