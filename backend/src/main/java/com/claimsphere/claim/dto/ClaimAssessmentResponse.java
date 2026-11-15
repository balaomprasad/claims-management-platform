package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimAssessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimAssessmentResponse(UUID id, UUID officerId, String officerName, String notes,
                                      BigDecimal recommendedAmount, Instant createdAt) {

    public static ClaimAssessmentResponse from(ClaimAssessment assessment) {
        return new ClaimAssessmentResponse(assessment.getId(), assessment.getOfficer().getId(),
                assessment.getOfficer().getFullName(), assessment.getNotes(), assessment.getRecommendedAmount(),
                assessment.getCreatedAt());
    }
}
