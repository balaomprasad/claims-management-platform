package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
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
        UUID claimantId,
        String claimantName,
        UUID assignedAdjusterId,
        String assignedAdjusterName,
        LocalDate incidentDate,
        String description,
        BigDecimal claimedAmount,
        BigDecimal approvedAmount,
        String rejectionReason,
        ClaimStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ClaimResponse from(Claim claim) {
        User adjuster = claim.getAssignedAdjuster();
        return new ClaimResponse(claim.getId(), claim.getClaimNumber(),
                claim.getPolicy().getId(), claim.getPolicy().getPolicyNumber(),
                claim.getClaimant().getId(), claim.getClaimant().getFullName(),
                adjuster == null ? null : adjuster.getId(), adjuster == null ? null : adjuster.getFullName(),
                claim.getIncidentDate(), claim.getDescription(), claim.getClaimedAmount(),
                claim.getApprovedAmount(), claim.getRejectionReason(), claim.getStatus(),
                claim.getCreatedAt(), claim.getUpdatedAt());
    }
}
