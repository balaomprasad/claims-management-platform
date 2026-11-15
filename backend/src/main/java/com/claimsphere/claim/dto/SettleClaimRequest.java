package com.claimsphere.claim.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param settlementDate optional; defaults to today (UTC). Must not be in the future.
 */
public record SettleClaimRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal settledAmount,
        LocalDate settlementDate,
        @Size(max = 100) String settlementReference) {
}
