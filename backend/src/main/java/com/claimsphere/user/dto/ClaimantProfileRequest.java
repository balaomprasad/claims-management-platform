package com.claimsphere.user.dto;

import com.claimsphere.common.domain.Market;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClaimantProfileRequest(
        @NotNull Market market,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "must contain digits, spaces, +, - or () only")
        String phone,
        @Size(max = 300) String address) {
}
