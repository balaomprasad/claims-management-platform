package com.claimsphere.policy.dto;

import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param currency optional; defaults to the market's local currency
 */
public record CreatePolicyRequest(
        @NotNull UUID holderId,
        @NotNull ProductType productType,
        @NotNull Market market,
        CurrencyCode currency,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal coverageAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal deductible,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
