package com.claimsphere.claim.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle states and the single source of truth for permitted transitions.
 *
 * <pre>
 * SUBMITTED -assign-> UNDER_REVIEW -approve-> APPROVED -settle-> SETTLED
 *                      |   ^    |-reject-> REJECTED
 *         request-info v   | provide-info
 *                   INFO_REQUESTED
 * SUBMITTED / UNDER_REVIEW / INFO_REQUESTED -withdraw-> WITHDRAWN
 * </pre>
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    APPROVED,
    REJECTED,
    SETTLED,
    WITHDRAWN;

    public Set<ClaimStatus> allowedTransitions() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW, WITHDRAWN);
            case UNDER_REVIEW -> EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED, WITHDRAWN);
            case INFO_REQUESTED -> EnumSet.of(UNDER_REVIEW, WITHDRAWN);
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

    /** States in which an officer is actively working the claim. */
    public boolean isInReview() {
        return this == UNDER_REVIEW || this == INFO_REQUESTED;
    }

    public static Set<ClaimStatus> openStatuses() {
        EnumSet<ClaimStatus> open = EnumSet.noneOf(ClaimStatus.class);
        for (ClaimStatus status : values()) {
            if (!status.isTerminal()) {
                open.add(status);
            }
        }
        return open;
    }
}
