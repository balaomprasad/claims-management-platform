package com.claimsphere.claim.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published claim lifecycle event (Kafka topic {@code claimsphere.claim-events}, keyed by claimId).
 * This record is the public contract between the claim module and its consumers; it deliberately
 * uses plain types (e.g. status names as strings) so consumers do not depend on claim internals.
 *
 * @param amount        settled amount for CLAIM_SETTLED, approved amount for CLAIM_APPROVED, otherwise the
 *                      estimated loss; always expressed in {@code currency}
 * @param correlationId the correlation id of the request that caused the event, for end-to-end tracing
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType eventType,
        int schemaVersion,
        Instant occurredAt,
        String correlationId,
        UUID claimId,
        String claimNumber,
        UUID claimantId,
        UUID assignedOfficerId,
        String fromStatus,
        String toStatus,
        BigDecimal amount,
        String currency,
        UUID actorId) {

    public static final int CURRENT_SCHEMA_VERSION = 2;
}
