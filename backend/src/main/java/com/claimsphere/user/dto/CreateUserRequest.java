package com.claimsphere.user.dto;

import com.claimsphere.common.domain.Market;
import com.claimsphere.user.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin-only payload for provisioning users with an explicit role.
 *
 * @param market required when {@code role} is CLAIMANT (creates the claimant profile), ignored otherwise
 */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull Role role,
        Market market) {
}
