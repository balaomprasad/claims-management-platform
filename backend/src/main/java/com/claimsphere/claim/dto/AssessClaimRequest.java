package com.claimsphere.claim.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AssessClaimRequest(
        @NotBlank @Size(max = 4000) String notes,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal recommendedAmount) {
}
