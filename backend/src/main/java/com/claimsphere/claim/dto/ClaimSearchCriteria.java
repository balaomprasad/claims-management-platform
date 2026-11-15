package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimPriority;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.ProductType;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Resolved, role-scoped search criteria used by the repository layer.
 */
public record ClaimSearchCriteria(
        UUID claimantId,
        Set<ClaimStatus> statuses,
        Market market,
        ProductType claimType,
        ClaimPriority priority,
        CurrencyCode currency,
        UUID assignedOfficerId,
        boolean unassignedOnly,
        LocalDate incidentFrom,
        LocalDate incidentTo,
        LocalDate submittedFrom,
        LocalDate submittedTo) {
}
