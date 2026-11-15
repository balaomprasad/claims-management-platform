package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.dto.ClaimHistoryResponse;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.ClaimSummaryResponse;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.claim.repository.ClaimStatusHistoryRepository;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side claim operations, scoped by the caller's role and ownership.
 */
@Service
@Transactional(readOnly = true)
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final ClaimAccessPolicy accessPolicy;

    public ClaimService(ClaimRepository claimRepository, ClaimStatusHistoryRepository historyRepository,
                        ClaimAccessPolicy accessPolicy) {
        this.claimRepository = claimRepository;
        this.historyRepository = historyRepository;
        this.accessPolicy = accessPolicy;
    }

    public Page<ClaimResponse> listClaims(CurrentUser user, ClaimStatus status, Pageable pageable) {
        Page<Claim> claims;
        if (user.is(Role.CLAIMANT)) {
            claims = status == null
                    ? claimRepository.findByClaimantId(user.id(), pageable)
                    : claimRepository.findByClaimantIdAndStatus(user.id(), status, pageable);
        } else {
            claims = status == null
                    ? claimRepository.findAllBy(pageable)
                    : claimRepository.findByStatus(status, pageable);
        }
        return claims.map(ClaimResponse::from);
    }

    public ClaimResponse getClaim(UUID claimId, CurrentUser user) {
        return ClaimResponse.from(loadVisibleClaim(claimId, user));
    }

    public List<ClaimHistoryResponse> getHistory(UUID claimId, CurrentUser user) {
        loadVisibleClaim(claimId, user);
        return historyRepository.findByClaimIdOrderByChangedAtAsc(claimId).stream()
                .map(ClaimHistoryResponse::from)
                .toList();
    }

    public ClaimSummaryResponse getSummary() {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            counts.put(status, 0L);
        }
        claimRepository.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new ClaimSummaryResponse(counts, total);
    }

    private Claim loadVisibleClaim(UUID claimId, CurrentUser user) {
        Claim claim = claimRepository.findDetailedById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
        accessPolicy.requireVisible(claim, user);
        return claim;
    }
}
