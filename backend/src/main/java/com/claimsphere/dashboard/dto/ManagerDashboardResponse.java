package com.claimsphere.dashboard.dto;

import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manager dashboard. Monetary figures are always grouped by currency; amounts in different currencies are never
 * added together because no exchange-rate source is configured.
 */
public record ManagerDashboardResponse(
        Instant generatedAt,
        DashboardFilter filter,
        long totalClaims,
        long outstandingClaims,
        long unassignedClaims,
        Map<ClaimStatus, Long> claimsByStatus,
        List<MarketSummary> claimsByMarket,
        List<CurrencyLiability> liabilityByCurrency,
        List<OfficerWorkload> officerWorkload,
        ProcessingTime processingTime,
        Ageing ageing,
        Map<String, String> definitions) {

    public record MarketSummary(Market market, long totalClaims, long outstandingClaims) {
    }

    /**
     * @param estimatedExposure  sum of estimated loss on claims not yet decided (SUBMITTED, UNDER_REVIEW,
     *                           INFO_REQUESTED)
     * @param approvedLiability  sum of approved amounts on claims awaiting settlement (APPROVED)
     * @param settledAmount      sum of settled amounts (SETTLED)
     */
    public record CurrencyLiability(CurrencyCode currency, BigDecimal estimatedExposure, BigDecimal approvedLiability,
                                    BigDecimal settledAmount, long outstandingClaims) {
    }

    public record OfficerWorkload(UUID officerId, String officerName, long openClaims, long decidedClaims,
                                  long settledClaims) {
    }

    /** Average days from submission to closure for claims closed by a decision (SETTLED or REJECTED). */
    public record ProcessingTime(Double averageDays, long closedClaims) {
    }

    public record Ageing(int thresholdDays, long beyondThreshold, List<AgeingBucket> buckets) {
    }

    public record AgeingBucket(String label, long claims) {
    }
}
