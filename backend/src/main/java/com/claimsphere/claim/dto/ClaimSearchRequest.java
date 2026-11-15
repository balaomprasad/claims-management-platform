package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimPriority;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.ProductType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Query parameters accepted by {@code GET /api/v1/claims}. All filters are optional.
 *
 * @param openOnly     only claims that are not yet closed (SETTLED, REJECTED, WITHDRAWN excluded)
 * @param unassigned   only claims without an assigned officer (the officer queue)
 * @param assignedToMe only claims assigned to the calling officer
 */
public record ClaimSearchRequest(
        ClaimStatus status,
        Market market,
        ProductType claimType,
        ClaimPriority priority,
        CurrencyCode currency,
        UUID assignedOfficerId,
        Boolean unassigned,
        Boolean assignedToMe,
        Boolean openOnly,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo) {

    public static ClaimSearchRequest empty() {
        return new ClaimSearchRequest(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
