package com.claimsphere.claim.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param adjusterId required for admins; adjusters may omit it (self-assignment) but cannot assign others
 */
public record AssignClaimRequest(UUID adjusterId, @Size(max = 1000) String comment) {
}
