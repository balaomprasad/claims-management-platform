package com.claimsphere.claim.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published claim lifecycle event (Kafka topic {@code claimsphere.claim-events}, keyed by claimId).
 * This record is the public contract between the claim module and its consumers; it deliberately
 * uses plain types (e.g. status names as strings) so consumers do not depend on claim internals.
 *
 * @param amount the approved amount for APPROVED/SETTLED events, otherwise the claimed amount
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID claimId,
        String claimNumber,
        UUID claimantId,
        UUID assignedAdjusterId,
        String fromStatus,
        String toStatus,
        BigDecimal amount,
        UUID actorId) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
