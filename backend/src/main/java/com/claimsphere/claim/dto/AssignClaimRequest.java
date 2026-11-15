package com.claimsphere.claim.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param officerId required for managers and admins; claims officers may omit it (self-assignment)
 *                  but cannot assign anyone else
 */
public record AssignClaimRequest(UUID officerId, @Size(max = 1000) String comment) {

    public static AssignClaimRequest self() {
        return new AssignClaimRequest(null, null);
    }
}
