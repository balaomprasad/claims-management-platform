package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.domain.ClaimStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record ClaimHistoryResponse(
        UUID id,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        UUID changedById,
        String changedByName,
        String comment,
        Instant changedAt) {

    public static ClaimHistoryResponse from(ClaimStatusHistory history) {
        return new ClaimHistoryResponse(history.getId(), history.getFromStatus(), history.getToStatus(),
                history.getChangedBy().getId(), history.getChangedBy().getFullName(),
                history.getComment(), history.getChangedAt());
    }
}
