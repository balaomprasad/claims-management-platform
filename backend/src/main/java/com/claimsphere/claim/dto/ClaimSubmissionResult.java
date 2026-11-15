package com.claimsphere.claim.dto;

/**
 * Result of an idempotent submission: {@code created} is false when an earlier request with the same
 * Idempotency-Key already created the claim.
 */
public record ClaimSubmissionResult(ClaimResponse claim, boolean created) {
}
