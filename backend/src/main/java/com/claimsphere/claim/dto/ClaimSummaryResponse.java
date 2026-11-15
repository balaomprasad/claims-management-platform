package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimStatus;

import java.util.Map;

public record ClaimSummaryResponse(Map<ClaimStatus, Long> countsByStatus, long total) {
}
