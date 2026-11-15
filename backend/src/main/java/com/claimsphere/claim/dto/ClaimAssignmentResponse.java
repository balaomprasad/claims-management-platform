package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimAssignment;

import java.time.Instant;
import java.util.UUID;

public record ClaimAssignmentResponse(UUID id, UUID officerId, String officerName, String assignedByName,
                                      Instant assignedAt) {

    public static ClaimAssignmentResponse from(ClaimAssignment assignment) {
        return new ClaimAssignmentResponse(assignment.getId(), assignment.getOfficer().getId(),
                assignment.getOfficer().getFullName(), assignment.getAssignedBy().getFullName(),
                assignment.getAssignedAt());
    }
}
