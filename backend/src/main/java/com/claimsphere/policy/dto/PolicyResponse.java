package com.claimsphere.policy.dto;

import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.domain.PolicyStatus;
import com.claimsphere.policy.domain.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyResponse(
        UUID id,
        String policyNumber,
        UUID holderId,
        String holderName,
        ProductType productType,
        Market market,
        CurrencyCode currency,
        BigDecimal coverageAmount,
        BigDecimal deductible,
        LocalDate startDate,
        LocalDate endDate,
        PolicyStatus status,
        Instant createdAt) {

    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(policy.getId(), policy.getPolicyNumber(), policy.getHolder().getId(),
                policy.getHolder().getFullName(), policy.getProductType(), policy.getMarket(), policy.getCurrency(),
                policy.getCoverageAmount(), policy.getDeductible(), policy.getStartDate(), policy.getEndDate(),
                policy.getStatus(), policy.getCreatedAt());
    }
}
