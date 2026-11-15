package com.claimsphere.user.dto;

import com.claimsphere.common.domain.Market;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload. Intentionally has no role field: self-registered users are always claimants.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull Market market,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "must contain digits, spaces, +, - or () only")
        String phone) {
}
