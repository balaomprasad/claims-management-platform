package com.claimsphere.claim.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Details a claimant may correct while the claim is SUBMITTED or INFO_REQUESTED.
 */
public record UpdateClaimRequest(
        @NotBlank @Size(max = 300) String incidentLocation,
        @NotBlank @Size(max = 2000) String incidentDescription,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal estimatedLoss) {
}
