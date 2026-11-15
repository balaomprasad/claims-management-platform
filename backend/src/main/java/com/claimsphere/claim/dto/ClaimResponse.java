package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimPriority;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.user.domain.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        String claimNumber,
        UUID policyId,
        String policyNumber,
        ProductType claimType,
        Market market,
        CurrencyCode currency,
        UUID claimantId,
        String claimantName,
        UUID assignedOfficerId,
        String assignedOfficerName,
        LocalDate incidentDate,
        String incidentLocation,
        String incidentDescription,
        BigDecimal estimatedLoss,
        BigDecimal approvedAmount,
        BigDecimal settledAmount,
        LocalDate settlementDate,
        String settlementReference,
        ClaimPriority priority,
        ClaimStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    public static ClaimResponse from(Claim claim) {
        User officer = claim.getAssignedOfficer();
        return new ClaimResponse(claim.getId(), claim.getClaimNumber(),
                claim.getPolicy().getId(), claim.getPolicy().getPolicyNumber(),
                claim.getClaimType(), claim.getMarket(), claim.getCurrency(),
                claim.getClaimant().getId(), claim.getClaimant().getFullName(),
                officer == null ? null : officer.getId(), officer == null ? null : officer.getFullName(),
                claim.getIncidentDate(), claim.getIncidentLocation(), claim.getIncidentDescription(),
                claim.getEstimatedLoss(), claim.getApprovedAmount(), claim.getSettledAmount(),
                claim.getSettlementDate(), claim.getSettlementReference(), claim.getPriority(), claim.getStatus(),
                claim.getCreatedAt(), claim.getUpdatedAt(), claim.getClosedAt());
    }
}
