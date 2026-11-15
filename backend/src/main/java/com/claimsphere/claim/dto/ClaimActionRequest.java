package com.claimsphere.claim.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional comment for actions that need no other input (settle, withdraw).
 */
public record ClaimActionRequest(@Size(max = 1000) String comment) {

    public static ClaimActionRequest empty() {
        return new ClaimActionRequest(null);
    }
}
